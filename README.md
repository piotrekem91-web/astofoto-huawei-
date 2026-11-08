# Astro Pro P40

Natywna aplikacja Android dla Huawei P40 Pro / EMUI, bez zależności od Google Mobile Services.

## Aktualny prototyp 0.2.0
- Podgląd aparatu przez Camera2
- Ręczne ISO i czas ekspozycji, jeśli firmware danego modułu udostępnia sterowanie manualne (suwaki są blokowane, gdy aparat nie zgłasza trybu manualnego)
- Dobór rozmiaru JPEG z rozmiarów zgłoszonych przez aparat
- Zapis zdjęć JPEG do Pictures/AstroProP40
- Seria 10 zdjęć; odstęp jest wydłużany automatycznie przy długich ekspozycjach (sekwencja plików, bez wyrównywania i stackingu)
- Preset nocny ISO 800 / do 10 s, ograniczany do zakresu zgłoszonego przez aparat
- Ręczna ostrość i ustawienie nieskończoności, jeśli moduł aparatu zgłasza obsługę ręcznego focusu
- Opcjonalne zdjęcia RAW/DNG przez Camera2, jeśli wybrany moduł udostępnia RAW_SENSOR; jeśli kombinacja strumieni RAW/JPEG nie zadziała, aplikacja wraca do podglądu JPEG
- Nazwy zdjęć z dokładnością do milisekund
- GitHub Actions: budowanie debug APK po zmianie kodu

## Budowanie APK
Wymagane JDK 17, Android SDK i Gradle 8.9 (Android Gradle Plugin 8.7.3). W Android Studio wybierz **Build > Build APK(s)**.
Po udanym przebiegu GitHub Actions plik `app-debug.apk` będzie w artefakcie `AstroProP40-debug`.

## Ważne ograniczenia
To prototyp aparatu, nie gotowa, przetestowana aplikacja astrofotograficzna. Wersja 0.2.1 nie jest jeszcze zweryfikowana kompilacją CI ani testem na fizycznym P40 Pro; nie instaluj jej jako wersji produkcyjnej. Nie ma jeszcze wyrównywania gwiazd ani stackowania, kalibracji dark/flat/bias ani edytora. Seria 10 ujęć automatyzuje kolejne zdjęcia, ale nie łączy ich w jeden obraz. RAW/DNG zależy od możliwości konkretnego aparatu i wymaga testu na urządzeniu. Limity i dostępność ekspozycji zależą od Camera2 oraz firmware Huawei. Workflow GitHub Actions uruchamia kompilację po pushu, dla pull requestów i ręcznie; po sukcesie udostępnia artefakt `AstroProP40-debug` przez 14 dni. Przed dystrybucją trzeba potwierdzić zielony build i sprawdzić działanie na fizycznym P40 Pro.
