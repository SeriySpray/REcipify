package com.example.recipefood.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.recipefood.R
import com.example.recipefood.ui.camera.CameraActivity
import com.example.recipefood.ui.history.HistoryActivity
import com.example.recipefood.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipefood.adapters.MealHistoryAdapter
import com.example.recipefood.ui.mealdetail.MealDetailActivity
import java.util.Calendar

class TrackingFragment : Fragment() {

    private lateinit var viewModel: DashboardViewModel
    private lateinit var mealAdapter: MealHistoryAdapter
    private var selectedDate: Date = Date()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_tracking, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dateTextView = view.findViewById<TextView>(R.id.dateTextView)
        val btnPrevDay = view.findViewById<View>(R.id.btnPrevDay)
        val btnNextDay = view.findViewById<View>(R.id.btnNextDay)
        
        val caloriesTodayTextView = view.findViewById<TextView>(R.id.caloriesTodayTextView)
        val progressBarContainer = view.findViewById<android.widget.FrameLayout>(R.id.progressBarContainer)
        val progressFill = view.findViewById<View>(R.id.progressFill)
        val caloriesGoalTextView = view.findViewById<TextView>(R.id.caloriesGoalTextView)
        
        val proteinsTextView = view.findViewById<TextView>(R.id.proteinsTextView)
        val fatsTextView = view.findViewById<TextView>(R.id.fatsTextView)
        val carbsTextView = view.findViewById<TextView>(R.id.carbsTextView)
        
        val proteinsGoalTextView = view.findViewById<TextView>(R.id.proteinsGoalTextView)
        val fatsGoalTextView = view.findViewById<TextView>(R.id.fatsGoalTextView)
        val carbsGoalTextView = view.findViewById<TextView>(R.id.carbsGoalTextView)

        val proteinsCircular = view.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.proteinsCircular)
        val fatsCircular = view.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.fatsCircular)
        val carbsCircular = view.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.carbsCircular)

        val streakTextView = view.findViewById<TextView>(R.id.streakTextView)
        val streakContainer = view.findViewById<View>(R.id.streakContainer)
        val rvMeals = view.findViewById<RecyclerView>(R.id.rvMeals)
        val tvEmptyMessage = view.findViewById<TextView>(R.id.tvEmptyMessage)

        val bg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(ContextCompat.getColor(requireContext(), R.color.surface_elevated))
        }
        progressBarContainer.background = bg

        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]

        // Setup RecyclerView
        mealAdapter = MealHistoryAdapter(
            onItemClick = { meal ->
                val intent = Intent(requireContext(), MealDetailActivity::class.java)
                intent.putExtra("meal_id", meal.id)
                startActivity(intent)
            },
            onDeleteClick = { meal ->
                viewModel.deleteMeal(meal)
            }
        )
        rvMeals.layoutManager = LinearLayoutManager(requireContext())
        rvMeals.adapter = mealAdapter

        viewModel.state.observe(viewLifecycleOwner) { state ->
            selectedDate = state.selectedDate
            updateDateText(dateTextView, state.selectedDate)
            
            caloriesTodayTextView.text = "${state.todayCalories.toInt()} ккал"
            val ratio = if (state.targetCalories > 0) (state.todayCalories / state.targetCalories).coerceIn(0.0, 1.0) else 0.0
            
            val fillColor = when {
                ratio < 0.75 -> android.graphics.Color.parseColor("#4CAF50")
                ratio < 1.0  -> android.graphics.Color.parseColor("#FF9800")
                else         -> android.graphics.Color.parseColor("#F44336")
            }

            val fillDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(fillColor)
            }
            progressFill.background = fillDrawable

            if (progressBarContainer.width > 0) {
                val params = progressFill.layoutParams
                params.width = (progressBarContainer.width * ratio).toInt()
                progressFill.layoutParams = params
            } else {
                progressBarContainer.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        progressBarContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        val totalWidth = progressBarContainer.width
                        val params = progressFill.layoutParams
                        params.width = (totalWidth * ratio).toInt()
                        progressFill.layoutParams = params
                    }
                })
            }

            caloriesGoalTextView.text = getString(R.string.of_goal, state.targetCalories.toInt())
            
            proteinsTextView.text = "${state.todayProteins.toInt()}"
            fatsTextView.text = "${state.todayFats.toInt()}"
            carbsTextView.text = "${state.todayCarbs.toInt()}"
            
            proteinsGoalTextView.text = "з ${state.targetProteins.toInt()}г"
            fatsGoalTextView.text = "з ${state.targetFats.toInt()}г"
            carbsGoalTextView.text = "з ${state.targetCarbs.toInt()}г"

            val pRatio = if (state.targetProteins > 0) ((state.todayProteins / state.targetProteins) * 100).toInt() else 0
            val fRatio = if (state.targetFats > 0) ((state.todayFats / state.targetFats) * 100).toInt() else 0
            val cRatio = if (state.targetCarbs > 0) ((state.todayCarbs / state.targetCarbs) * 100).toInt() else 0

            proteinsCircular.progress = min(pRatio, 100)
            fatsCircular.progress = min(fRatio, 100)
            carbsCircular.progress = min(cRatio, 100)

            streakTextView.text = state.streak.toString()
            streakContainer.visibility = if (isToday(state.selectedDate)) View.VISIBLE else View.GONE
            
            mealAdapter.submitList(state.meals)
            tvEmptyMessage.visibility = if (state.meals.isEmpty()) View.VISIBLE else View.GONE
        }

        // Date navigation
        btnPrevDay.setOnClickListener {
            val cal = Calendar.getInstance().apply { time = selectedDate }
            cal.add(Calendar.DAY_OF_YEAR, -1)
            viewModel.setDate(cal.time)
        }

        btnNextDay.setOnClickListener {
            val cal = Calendar.getInstance().apply { time = selectedDate }
            if (isToday(selectedDate)) return@setOnClickListener
            cal.add(Calendar.DAY_OF_YEAR, 1)
            viewModel.setDate(cal.time)
        }

        dateTextView.setOnClickListener {
            val cal = Calendar.getInstance().apply { time = selectedDate }
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d)
                viewModel.setDate(cal.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.maxDate = System.currentTimeMillis()
                show()
            }
        }

        view.findViewById<View>(R.id.btnAnalyze).setOnClickListener {
            startActivity(Intent(requireContext(), CameraActivity::class.java))
        }

        view.findViewById<View>(R.id.btnManualAdd).setOnClickListener {
            val emptyFood = com.example.recipefood.model.Food(
                name = "",
                products = mutableListOf(),
                nutrition = null
            )
            val intent = Intent(requireContext(), com.example.recipefood.ui.editproducts.EditProductsActivity::class.java)
            intent.putExtra("food_json", com.google.gson.Gson().toJson(emptyFood))
            startActivity(intent)
        }
    }

    private fun updateDateText(textView: TextView, date: Date) {
        val today = Calendar.getInstance()
        val selected = Calendar.getInstance().apply { time = date }
        
        textView.text = if (today.get(Calendar.YEAR) == selected.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == selected.get(Calendar.DAY_OF_YEAR)) {
            "Сьогодні"
        } else {
            java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale("uk")).format(date)
        }
    }

    private fun isToday(date: Date): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance().apply { time = date }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) viewModel.refresh()
    }
}
