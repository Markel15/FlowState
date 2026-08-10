# FlowState

**A modern, intuitive task management app built with Jetpack Compose and Clean Architecture**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-1.10.1-blue)](fhttps://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room-2.8.4-green)](https://developer.android.com/training/data-storage/room)
[![Hilt](https://img.shields.io/badge/Hilt-2.57.2-red)](https://dagger.dev/hilt/)


## Screenshots

<p align="center" width="100%">
  <img src="screenshots/Frame1.png" width="32%" /><img src="screenshots/Frame2.png" width="32%" /><img src="screenshots/Frame3.png" width="32%" /><img src="screenshots/Frame4.png" width="32%" /><img src="screenshots/Frame5.png" width="32%" /><img src="screenshots/Frame6.png" width="32%" />
</p>

## Features

### Implemented
- **Task Management**: Create, edit and delete tasks
- **Priority System**: Set task priorities (High, Medium, Low, None)
- **Sub-tasks**: Break down tasks into manageable sub-tasks
- **Swipe Actions**: Swipe to delete tasks with smooth animations
- **Drag & Drop**: Reorder tasks via intuitive drag handles
- **Auto-save**: Changes are saved automatically when editing a task
- **Material 3**: Modern design
- **Task Scheduling**: With due dates and reminders
- **Calendar** view
- **Habit Tracking**: Build daily routines and track streaks
- **Widgets** for basic habits

### In Development
- **Widgets**: More widgets
- **Mood Tracking**: Log daily emotions and view trends
- **Improve UX**: with the subtasks
- Any new **suggestion**

## Motivation

I wanted to build a fully-featured Android app completely **free**, **ad-free**, and **privacy-focused** — no servers, no tracking, no data collection, works 100% offline.
This project was mainly a way for me to learn modern **Kotlin** (coming from a Java-only background) while creating something truly **customizable** that I (and others) can easily modify, extend and adapt to different needs.

## Getting Started

### Prerequisites
- Android Studio
- Android SDK 31 (Android 12)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/Markel15/flowstate.git
   cd flowstate
  
2. **Open in Android Studio**

    -Open Android Studio

    -Select "Open an Existing Project"

    -Navigate to the cloned directory

3. **Build and Run**

    -Connect an Android device or start an emulator (API 31+)

    -Click the Run button

## How to Contribute

We welcome contributions! Here's how you can help:

### Reporting Bugs
1. Check the [Issues](https://github.com/Markel15/flowstate/issues) to avoid duplicates
2. Create a new issue with:
   - Clear description
   - Steps to reproduce
   - Expected vs actual behavior
   - Screenshots/videos if possible
   - Device/OS information

### Suggesting Features
1. Check existing feature requests
2. Create an issue with:
   - Use case description
   - Mockups/wireframes if applicable
   - Priority justification

### Submitting Code
1. **Fork the repository**
2. **Create a feature branch**
   ```bash
   git checkout -b feature/amazing-feature
3. **Make your changes**

  - Write tests when possible
  - Update documentation when possible
4. **Commit your changes**
   ```bash
   git commit -m 'Add some amazing feature'
5. Push to the branch
   ```bash
   git push origin feature/amazing-feature
6. Open a Pull Request

## Architecture

FlowState follows **Clean Architecture** with a clear separation of concerns:

```text
UI Layer (Presentation)
├── ViewModels
├── Composable Screens
└── UI State Management

Domain Layer (Business Logic)
├── Use Cases (Interactors)
├── Repository Interfaces
└── Domain Models (Task, SubTask, Priority)

Data Layer (Infrastructure)
├── Repository Implementations
├── Local Data Source (Room)
└── Data Models (Entities)
```


## Tech Stack

| Component      | Technology                |
|----------------|---------------------------|
| **UI Framework** | Jetpack Compose           |
| **Architecture** | Clean Architecture + MVVM |
| **Database**     | Room                      |
| **DI**           | Hilt                      |
| **Async**        | Kotlin Coroutines         |
| **Navigation**   | Navigation 3              |
| **Animations**   | Compose Animation APIs    |
