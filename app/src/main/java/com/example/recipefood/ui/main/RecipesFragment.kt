package com.example.recipefood.ui.main

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipefood.R
import com.example.recipefood.algorithms.SortingAlgorithms
import com.example.recipefood.model.Recipe
import com.example.recipefood.ui.add.AddRecipeActivity
import com.example.recipefood.ui.detail.RecipeDetailActivity
import com.example.recipefood.ui.history.HistoryActivity
import com.example.recipefood.ui.settings.SettingsActivity
import com.example.recipefood.viewmodel.RecipeViewModel

class RecipesFragment : Fragment() {

    private lateinit var viewModel: RecipeViewModel
    private lateinit var gridAdapter: RecipeGridAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText

    private var allRecipes = listOf<Recipe>()

    private var isFabMenuOpen = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_recipes, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewRecipes)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        etSearch = view.findViewById(R.id.etSearch)

        gridAdapter = RecipeGridAdapter { recipe -> openRecipeDetail(recipe) }
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerView.adapter = gridAdapter
        recyclerView.isNestedScrollingEnabled = false

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString() ?: "")
            }
        })

        val fabAdd = view.findViewById<View>(R.id.fabAdd)
        val fabManualContainer = view.findViewById<View>(R.id.fabManualContainer)
        val fabPhotoContainer = view.findViewById<View>(R.id.fabPhotoContainer)
        val fabAddManual = view.findViewById<View>(R.id.fabAddManual)
        val fabAddPhoto = view.findViewById<View>(R.id.fabAddPhoto)

        fabAdd.setOnClickListener {
            toggleFabMenu(fabAdd, fabManualContainer, fabPhotoContainer)
        }

        fabAddManual.setOnClickListener {
            toggleFabMenu(fabAdd, fabManualContainer, fabPhotoContainer)
            startActivity(Intent(requireContext(), AddRecipeActivity::class.java))
        }

        fabAddPhoto.setOnClickListener {
            toggleFabMenu(fabAdd, fabManualContainer, fabPhotoContainer)
            val intent = Intent(requireContext(), com.example.recipefood.ui.camera.CameraActivity::class.java)
            intent.putExtra("GENERATE_RECIPE_MODE", true)
            startActivity(intent)
        }

        view.findViewById<View>(R.id.btnMenu).setOnClickListener { anchor ->
            val themedCtx = ContextThemeWrapper(requireContext(), R.style.PopupThemeOverlay)
            val popup = PopupMenu(themedCtx, anchor)
            popup.menu.apply {
                add(0, R.id.action_sort, 0, getString(R.string.menu_sort))
                add(0, R.id.action_settings, 1, getString(R.string.settings_title))
            }
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_sort -> { showSortDialog(); true }
                    R.id.action_settings -> { startActivity(Intent(requireContext(), SettingsActivity::class.java)); true }
                    else -> false
                }
            }
            popup.show()
        }

        viewModel = ViewModelProvider(this)[RecipeViewModel::class.java]

        viewModel.allRecipes.observe(viewLifecycleOwner) { recipes ->
            allRecipes = recipes ?: emptyList()
            applyFilter(etSearch.text.toString())
        }

        viewModel.sortedRecipes.observe(viewLifecycleOwner) { recipes ->
            allRecipes = recipes ?: emptyList()
            applyFilter(etSearch.text.toString())
        }
    }

    private fun toggleFabMenu(mainFab: View, manual: View, photo: View) {
        isFabMenuOpen = !isFabMenuOpen
        
        if (isFabMenuOpen) {
            mainFab.animate().rotation(45f).setDuration(200).start()
            
            manual.visibility = View.VISIBLE
            manual.animate().alpha(1f).translationY(-20f).setDuration(200).start()
            
            photo.visibility = View.VISIBLE
            photo.animate().alpha(1f).translationY(-20f).setDuration(200).setStartDelay(50).start()
        } else {
            mainFab.animate().rotation(0f).setDuration(200).start()
            
            manual.animate().alpha(0f).translationY(0f).setDuration(200).withEndAction {
                manual.visibility = View.GONE
            }.start()
            
            photo.animate().alpha(0f).translationY(0f).setDuration(200).withEndAction {
                photo.visibility = View.GONE
            }.start()
        }
    }

    private fun applyFilter(query: String) {
        val filtered = if (query.isBlank()) {
            allRecipes
        } else {
            val q = query.trim().lowercase()
            allRecipes.filter { it.name.lowercase().contains(q) }
        }
        gridAdapter.submitList(filtered)
        if (filtered.isEmpty()) {
            tvEmpty.text = if (query.isBlank())
                "Рецептів поки що немає.\nНатисніть + щоб додати."
            else
                "Нічого не знайдено.\nСпробуйте інший запит."
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun openRecipeDetail(recipe: Recipe) {
        startActivity(Intent(requireContext(), RecipeDetailActivity::class.java).apply {
            putExtra("RECIPE_ID", recipe.id)
        })
    }

    private fun showSortDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sort, null)
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.sortByName).setOnClickListener {
            viewModel.sortRecipes(allRecipes, SortingAlgorithms.SortType.NAME)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.sortByTime).setOnClickListener {
            viewModel.sortRecipes(allRecipes, SortingAlgorithms.SortType.TIME_ASC)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.sortByDifficulty).setOnClickListener {
            viewModel.sortRecipes(allRecipes, SortingAlgorithms.SortType.DIFFICULTY)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.sortByDate).setOnClickListener {
            viewModel.sortRecipes(allRecipes, SortingAlgorithms.SortType.DATE)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.sortByCalories).setOnClickListener {
            viewModel.sortRecipes(allRecipes, SortingAlgorithms.SortType.CALORIES_ASC)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }
}
