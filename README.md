# Android-Offline-Meeting-Recorder

An experimental Android application that records audio, performs speech recognition **offline**, and generates summaries using a local LLM.

This project demonstrates how to run **fully offline AI processing on Android devices** using native libraries.

## Features

* 🎤 Audio recording on Android
* 🗣 Offline speech recognition
* 📝 Automatic meeting transcription
* 🤖 Local AI summarization
* 🔒 Fully offline (no cloud API required)

## Architecture

The processing pipeline is:

AudioRecord
↓
PCM audio file
↓
WAV conversion
↓
Speech recognition (whisper.cpp)
↓
Transcribed text
↓
Text summarization (llama.cpp + HACHI-Summary)
↓
Summary output

## Technologies

* Android (Kotlin)
* JNI (C++)
* Native inference engines

Libraries used:

* whisper.cpp
* llama.cpp

## AI Models

This repository **does not include model files** because they are too large for GitHub.

You need to download them separately.

Example models:

Whisper model
Place in:

app/src/main/assets/models/

Example file:

ggml-base.bin

LLM summary model example:

HACHI-Summary-Ja-sarashina2.2-0.5b-instruct

## Build Instructions

1. Clone the repository

2. Open the project in Android Studio

3. Download required AI models

4. Place the models in:

app/src/main/assets/models/

5. Build the project

## Project Structure

app/
Android application code

lib/
Native library wrapper

gradle/
Gradle configuration

## Why This Project Exists

Many speech-to-text and summarization tools rely on cloud APIs.

This project explores a different approach:

Running **speech recognition and LLM summarization directly on a smartphone**.

This enables:

* offline meetings
* privacy-friendly processing
* edge AI experimentation

## Status

Experimental / Work in progress.

The project is being actively developed while exploring improvements in:

* inference speed
* summarization quality
* real-time transcription

## Author

Developed as a personal experiment in offline AI applications on Android.

## License

MIT License
