import requests
import json
import base64
import time
import os
from PIL import Image
import io

# Configuration
API_KEY = "YOUR_GROQ_API_KEY"
API_URL = "https://api.groq.com/openai/v1/chat/completions"
VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
TEXT_MODEL = "llama-3.3-70b-versatile"

DATASET = [
    {
        "id": "caesar_salad",
        "name": "Caesar Salad",
        "file": "ceasar.jpg",
        "expected": {"calories": 450, "proteins": 25.0, "fats": 30.0, "carbs": 15.0},
        "ingredients": ["Салат ромен", "Курка", "Пармезан", "Сухарики", "Соус"]
    },
    {
        "id": "banana_bread",
        "name": "Banana Bread",
        "file": "banana_bread.jpg",
        "expected": {"calories": 320, "proteins": 4.5, "fats": 12.0, "carbs": 50.0},
        "ingredients": ["Банан", "Борошно", "Цукор", "Яйця", "Масло"]
    },
    {
        "id": "pelmeni",
        "name": "Pelmeni",
        "file": "pelmeni.jpg",
        "expected": {"calories": 500, "proteins": 18.0, "fats": 22.0, "carbs": 55.0},
        "ingredients": ["Тісто", "М'ясний фарш", "Цибуля"]
    },
    {
        "id": "fruits_bowl",
        "name": "Assorted Fruits",
        "file": "fruits.jpg",
        "expected": {"calories": 160, "proteins": 2.0, "fats": 0.5, "carbs": 38.0},
        "ingredients": ["Різні фрукти"]
    },
    {
        "id": "beef_burger",
        "name": "Classic Beef Burger",
        "file": "burger.jpg",
        "expected": {"calories": 750, "proteins": 35.0, "fats": 45.0, "carbs": 50.0},
        "ingredients": ["Булка", "Яловича котлета", "Сир", "Салат", "Томат"]
    },
    {
        "id": "margherita_pizza",
        "name": "Pizza Margherita",
        "file": "pizza.jpg",
        "expected": {"calories": 800, "proteins": 30.0, "fats": 25.0, "carbs": 110.0},
        "ingredients": ["Тісто", "Томатний соус", "Моцарела"]
    },
    {
        "id": "borscht",
        "name": "Ukrainian Borscht",
        "file": "borscht.jpg",
        "expected": {"calories": 250, "proteins": 10.0, "fats": 12.0, "carbs": 25.0},
        "ingredients": ["Буряк", "Капуста", "Картопля", "Яловичина"]
    }
]

DEFAULT_VISION_PROMPT = """
Проаналізуй це фото страви. Твоя мета - максимально точно визначити інгредієнти та їхню вагу для ОДНІЄЇ ПОРЦІЇ.

КРИТИЧНІ ПРАВИЛА:
1. МАКСИМАЛЬНА ВАГА: Одна порція (тарілка) рідко перевищує 400-500г сумарно. Якщо твій розрахунок більший - перевір масштаби ще раз.
2. ПИРІГ/ХЛІБ: Якщо на фото скибка - це 80-120г. Не пиши вагу цілого виробу.
3. ПІЦА: Одна скибка - це 100-150г (тісто 70г, сир 30-40г). Не пиши 150г сиру на шматок!
4. ІДЕНТИФІКАЦІЯ: Будь уважним з начинками. Пельмені (м'ясо) ≠ Вареники (картопля/сир). Дивись на форму та контекст.
5. САНІТІ-ЧЕК: Чи реально з'їсти таку кількість інгредієнтів за один раз? Враховуй щільність продуктів.

Поверни результат ТІЛЬКИ у форматі JSON українською мовою:
{
  "name": "Назва страви",
  "products": [
    {"name": "Інгредієнт", "weight": вага_в_г}
  ]
}
"""

def encode_image(image_path):
    if not os.path.exists(image_path):
        return None
    
    # Open and resize if necessary to avoid API limits
    with Image.open(image_path) as img:
        # Resize to max 1024 on longest side while keeping aspect ratio
        max_size = 1024
        if max(img.size) > max_size:
            ratio = max_size / float(max(img.size))
            new_size = tuple([int(x * ratio) for x in img.size])
            img = img.resize(new_size, Image.Resampling.LANCZOS)
        
        # Convert to RGB if needed
        if img.mode != 'RGB':
            img = img.convert('RGB')
            
        buffered = io.BytesIO()
        img.save(buffered, format="JPEG", quality=85)
        return base64.b64encode(buffered.getvalue()).decode('utf-8')

def groq_request(model, messages, temperature=0.4, retries=5):
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {API_KEY}"
    }
    payload = {
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "response_format": {"type": "json_object"}
    }
    
    for i in range(retries):
        try:
            response = requests.post(API_URL, headers=headers, json=payload, timeout=30)
            data = response.json()
            
            if 'error' in data:
                err_msg = data['error']['message']
                if "Rate limit" in err_msg or response.status_code == 429:
                    wait = (i + 1) * 7
                    print(f"\nRate limited. Waiting {wait}s before retry {i+1}/{retries}...")
                    time.sleep(wait)
                    continue
                else:
                    print(f"\nAPI Error: {err_msg}")
                    return data
            
            if 'choices' in data:
                return data
        except Exception as e:
            print(f"\nRequest failed: {str(e)}. Retrying...")
            time.sleep(5)
            
    return {"error": {"message": "Max retries exceeded"}}

def analyze_food(image_base64, prompt):
    messages = [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_base64}"}}
            ]
        }
    ]
    res = groq_request(VISION_MODEL, messages)
    if 'choices' not in res:
        raise Exception(f"Vision API Failed: {res.get('error', {}).get('message', 'Unknown error')}")
    return json.loads(res['choices'][0]['message']['content'])

def analyze_nutrition(food_data):
    products_text = "\n".join([f"- {p['name']}: {p['weight']}g" for p in food_data['products']])
    prompt = f"Calculate nutrition for {food_data['name']} with ingredients:\n{products_text}\nReturn JSON: {{'calories': 0, 'proteins': 0, 'fats': 0, 'carbs': 0}}"
    messages = [{"role": "user", "content": prompt}]
    res = groq_request(TEXT_MODEL, messages, temperature=0.2)
    if 'choices' not in res:
         raise Exception(f"Text API Failed: {res.get('error', {}).get('message', 'Unknown error')}")
    return json.loads(res['choices'][0]['message']['content'])

def run_benchmark(custom_prompt=None):
    prompt = custom_prompt or DEFAULT_VISION_PROMPT
    results = []
    
    base_dir = os.path.dirname(os.path.abspath(__file__))
    images_dir = os.path.join(base_dir, "images")
    
    print(f"\n--- Starting Benchmark ---")
    
    for case in DATASET:
        print(f"\n[{case['name']}]")
        img_path = os.path.join(images_dir, case['file'])
        
        if not os.path.exists(img_path) or os.path.getsize(img_path) < 100:
             print(f"  SKIPPED: Invalid or missing image")
             continue

        try:
            b64 = encode_image(img_path)
            time.sleep(2) 
            
            food = analyze_food(b64, prompt)
            print(f"  AI identified: {food['name']}")
            print(f"  AI ingredients: {', '.join([f['name']+'('+str(f['weight'])+'g)' for f in food.get('products', [])])}")
            
            nutr = analyze_nutrition(food)
            exp = case['expected']
            
            err = abs(nutr['calories'] - exp['calories']) / exp['calories'] * 100
            
            print(f"  Calories -> Actual: {nutr['calories']:.0f} | Expected: {exp['calories']:.0f} | Error: {err:.1f}%")
            print(f"  Macros   -> P: {nutr['proteins']:.1f}/{exp['proteins']:.1f} | F: {nutr['fats']:.1f}/{exp['fats']:.1f} | C: {nutr['carbs']:.1f}/{exp['carbs']:.1f}")

            results.append({"case": case['name'], "error": err})
        except Exception as e:
            print(f"  FAILED: {str(e)}")
            
    if results:
        avg_err = sum(r['error'] for r in results) / len(results)
        print(f"\n======================================")
        print(f"FINAL RESULT: Average Calorie Error = {avg_err:.2f}%")
        print(f"======================================\n")
    else:
        print("\nNo valid tests completed.")

if __name__ == "__main__":
    if not os.path.exists("images"):
        os.makedirs("images")
        print("Created 'images' folder. Please add test images there.")
    else:
        run_benchmark()
