package com.example.recipefood.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.ByteArrayOutputStream
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
            contentResolver.openInputStream(it)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                analyzeImage(bitmap)
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

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    analyzeImage(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "Помилка фото", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnCapture.isEnabled = true
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun analyzeImage(bitmap: Bitmap) {
        val isRecipeMode = intent.getBooleanExtra("RECIPE_MODE", false)
        val isGenerateRecipeMode = intent.getBooleanExtra("GENERATE_RECIPE_MODE", false)
        
        lifecycleScope.launch {
            try {
                val base64Image = withContext(Dispatchers.IO) {
                    bitmapToBase64(bitmap)
                }

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

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
