package com.example.recipefood.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.recipefood.RecipeFoodApp
import com.example.recipefood.R
import com.example.recipefood.databinding.ActivityCameraBinding
import com.example.recipefood.ui.add.AddRecipeActivity
import com.example.recipefood.ui.editproducts.EditProductsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val groqService by lazy { (application as RecipeFoodApp).groqService }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Потрібен дозвіл на камеру", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            binding.progressBar.visibility = android.view.View.VISIBLE
            binding.btnCapture.isEnabled = false
            
            lifecycleScope.launch {
                try {
                    val base64 = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(it)?.use { inputStream ->
                            val bytes = inputStream.readBytes()
                            Base64.encodeToString(bytes, Base64.NO_WRAP)
                        }
                    }
                    if (base64 != null) {
                        analyzeImage(base64)
                    } else {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.btnCapture.isEnabled = true
                        Toast.makeText(this@CameraActivity, "Помилка читання файлу", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnCapture.isEnabled = true
                    Toast.makeText(this@CameraActivity, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        binding.btnGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                .setResolutionStrategy(
                    androidx.camera.core.resolutionselector.ResolutionStrategy(
                        android.util.Size(1920, 1080),
                        androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                    )
                )
                .build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Помилка запуску камери", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnCapture.isEnabled = false

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    lifecycleScope.launch(Dispatchers.Default) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        image.close()
                        
                        withContext(Dispatchers.Main) {
                            analyzeImage(base64)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "Помилка фото", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnCapture.isEnabled = true
                }
            }
        )
    }

    private fun analyzeImage(base64Image: String) {
        val isRecipeMode = intent.getBooleanExtra("RECIPE_MODE", false)
        val isGenerateRecipeMode = intent.getBooleanExtra("GENERATE_RECIPE_MODE", false)
        
        lifecycleScope.launch {
            try {
                if (isGenerateRecipeMode) {
                    val recipe = withContext(Dispatchers.IO) {
                        groqService.analyzeRecipeFromImage(base64Image)
                    }
                    binding.progressBar.visibility = android.view.View.GONE
                    
                    val resultIntent = Intent().apply {
                        putExtra("recipe_json", Gson().toJson(recipe))
                    }
                    setResult(RESULT_OK, resultIntent)
                    
                    if (callingActivity == null) {
                        val intent = Intent(this@CameraActivity, AddRecipeActivity::class.java)
                        intent.putExtra("recipe_json", Gson().toJson(recipe))
                        startActivity(intent)
                    }
                    finish()
                } else if (isRecipeMode) {
                    val food = withContext(Dispatchers.IO) {
                        groqService.analyzeFood(base64Image)
                    }
                    val analyzedFood = withContext(Dispatchers.IO) {
                        groqService.analyzeNutrition(food)
                    }
                    binding.progressBar.visibility = android.view.View.GONE
                    val nutrition = analyzedFood.nutrition
                    if (nutrition != null) {
                        val resultIntent = Intent().apply {
                            putExtra("calories", nutrition.calories)
                            putExtra("proteins", nutrition.proteins)
                            putExtra("fats", nutrition.fats)
                            putExtra("carbs", nutrition.carbs)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    } else {
                        binding.btnCapture.isEnabled = true
                        Toast.makeText(this@CameraActivity, "Не вдалося розрахувати КБЖВ", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val food = withContext(Dispatchers.IO) {
                        groqService.analyzeFood(base64Image)
                    }
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnCapture.isEnabled = true
                    val intent = Intent(this@CameraActivity, EditProductsActivity::class.java)
                    intent.putExtra("food_json", Gson().toJson(food))
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnCapture.isEnabled = true
                
                if (e.message == "NOT_FOOD") {
                    showNotFoodDialog()
                } else {
                    Toast.makeText(this@CameraActivity, "Помилка аналізу: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showNotFoodDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_humor, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        dialogView.findViewById<android.widget.TextView>(R.id.tvHumorTitle).text = "Ой, халепа!"
        dialogView.findViewById<android.widget.TextView>(R.id.tvHumorMessage).text = 
            "Схоже, це не зовсім їстівна штука... Навіть наш ШІ не наважився це куштувати! Спробуйте сфотографувати справжню страву."
            
        dialogView.findViewById<android.view.View>(R.id.btnHumorConfirm).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
