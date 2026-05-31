# 🥘 REcipify (AI-Powered)

An advanced Android application designed to revolutionize how you manage recipes and track nutrition. Powered by **Groq AI**, REcipify provides intelligent analysis, seamless organization, and a modern user experience.

## ✨ Key Features

### 🤖 AI-Powered Intelligence
- **Intelligent Recipe Parsing:** Extract ingredients and nutrition info automatically using Groq AI.
- **Smart Suggestions:** Get advice on meal preparation and nutritional balance.
- **Fast Inference:** Leveraging Groq's LPU™ technology for near-instant AI responses.

### 📚 Advanced Recipe Management
- **Folder Organization:** Group your recipes into custom folders (Breakfast, Lunch, Keto, etc.).
- **Dynamic Search & Sort:** Specialized algorithms to find exactly what you need based on time, difficulty, or ingredients.
- **Offline First:** Full functionality without internet access using **Room Database**.

### 📊 Health & Tracking
- **Nutrition Dashboard:** Track calories, proteins, fats, and carbs in real-time.
- **Meal History:** Keep a detailed log of your past meals to monitor your progress.
- **Custom Progress Views:** Beautifully rendered UI components for visual tracking.

### 📸 Seamless Experience
- **Camera Integration:** Quick access for capturing recipe photos.
- **Modern UI/UX:** Responsive design with support for **Dark Mode**.
- **Package Migration:** Clean architecture following the latest Android best practices.

## 🛠 Tech Stack

- **Language:** Kotlin
- **UI Framework:** Android XML with Custom Views
- **Database:** Room Persistence Library (SQLite)
- **AI Integration:** Groq API
- **Architecture:** MVVM (Model-View-ViewModel)
- **Networking:** Retrofit / OkHttp
- **Dependency:** Coroutines for asynchronous operations

## 🚀 Getting Started

### Prerequisites
- Android Studio Iguana or newer
- JDK 17
- Groq Cloud API Key (for AI features)

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/SeriySpray/FoodAnalyzer.git
   ```
2. Open the project in **Android Studio**.
3. Create a `keys.properties` file in the root directory and add your Groq API key:
   ```properties
   GROQ_API_KEY=your_actual_api_key_here
   ```
4. Build and run the app on your emulator or physical device.

## 📦 Project Structure
```text
com.example.recipefood
├── adapters      # RecyclerView adapters for lists and grids
├── algorithms    # Search and sorting implementations
├── api           # AI service and network configurations
├── data          # DAOs, Repositories, and Database setup
├── model         # Data classes for Recipes, Meals, and Users
├── ui            # Activities and Fragments
├── viewmodel     # Business logic and state management
└── views         # Custom UI components (Progress bars, etc.)
```

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
*Created by [SeriySpray](https://github.com/SeriySpray)*
