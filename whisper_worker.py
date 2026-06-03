#!/usr/bin/env python3
"""
Whisper Local Transcription Server (Flask Worker)
=================================================
This script runs a local HTTP server on your PC that the Android app can upload 
recorded audio files to. It uses the Python 'whisper' library to process files locally.

No paid APIs, fully offline, and high speed using your PC's GPU or CPU!
The Whisper model is downloaded ONCE on the first run and stored locally.

How to run:
----------
1. Install Python 3 (https://www.python.org/)
2. Install dependencies:
   pip install flask openai-whisper torch
3. Run this server on your PC:
   python whisper_worker.py
4. In the Android App settings, select "Свой Whisper воркер" and:
   - If using the Android Emulator on the same PC: keep 'http://10.0.2.2:5000/transcribe'
   - If using a real Android phone on the same WiFi: change to 'http://<YOUR_PC_IP>:5000/transcribe'
"""

import os
import tempfile
import sys

try:
    from flask import Flask, request, jsonify
except ImportError:
    print("Error: Flask is not installed. Please run: pip install flask")
    sys.exit(1)

try:
    import whisper
except ImportError:
    print("Error: openai-whisper is not installed. Please run: pip install openai-whisper")
    sys.exit(1)

app = Flask(__name__)

# Load model globally so it stays in memory after the first load
model = None

def get_model():
    global model
    if model is None:
        print("\n" + "="*60)
        print("Загрузка модели Whisper (может занять несколько минут при первом запуске)...")
        print("Модель будет сохранена локально и в следующий раз загрузится мгновенно.")
        print("="*60 + "\n")
        
        # Available models: "tiny", "base", "small", "medium", "large"
        # "base" is the best balance of speed and quality for general transcription.
        model = whisper.load_model("base")
        print("\nМодель загружена успешно! Сервер готов принимать аудиофайлы.")
    return model

@app.route("/transcribe", methods=["POST"])
def transcribe():
    if "file" not in request.files:
        return jsonify({"error": "В запросе отсутствует поле 'file'"}), 400

    audio_file = request.files["file"]
    if audio_file.filename == "":
        return jsonify({"error": "Пустое имя файла"}), 400

    # Save to a secure temp path
    temp_dir = tempfile.gettempdir()
    temp_path = os.path.join(temp_dir, audio_file.filename)
    audio_file.save(temp_path)

    print(f"\n[Запрос] Получен файл {audio_file.filename} для расшифровки.")

    try:
        model_instance = get_model()
        
        # Perform local/GPU transcription with word timestamps enabled
        print("Запуск декодирования Whisper...")
        result = model_instance.transcribe(temp_path)
        print("Декодирование успешно завершено!")

        # Returning complete JSON containing text & segments
        return jsonify({
            "text": result.get("text", ""),
            "segments": result.get("segments", [])
        })

    except Exception as e:
        print(f"[Ошибка] Сбой расшифровки: {str(e)}")
        return jsonify({"error": str(e)}), 500
    finally:
        # Clean up temp file
        if os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except Exception:
                pass

@app.route("/ping", methods=["GET"])
def ping():
    return jsonify({"status": "ok", "worker": "whisper_local_flask"})

if __name__ == "__main__":
    # Run on all network interfaces (important for real devices to access it via local WiFi)
    app.run(host="0.0.0.0", port=5000, debug=False)
