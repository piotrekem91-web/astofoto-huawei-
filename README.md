# Astro Pro P40

Natywna aplikacja Android dla Huawei P40 Pro / EMUI, bez zależności od Google Mobile Services.

## Aktualny prototyp 0.1
- Podgląd aparatu przez Camera2
- Ręczne ISO i czas ekspozycji, jeśli firmware danego modułu udostępnia sterowanie manualne
- Dobór rozmiaru JPEG z rozmiarów zgłoszonych przez aparat
- Zapis zdjęć JPEG do Pictures/AstroProP40
- Seria 10 zdjęć; odstęp jest wydłużany automatycznie przy długich ekspozycjach (sekwencja plików, bez wyrównywania i stackingu)
- Preset nocny ISO 800 / do 10 s, ograniczany do zakresu zgłoszonego przez aparat
- Ręczna ostrość i ustawienie nieskończoności, jeśli moduł aparatu zgłasza obsługę ręcznego focusu
- Nazwy zdjęć z dokładnością do milisekund
- GitHub Actions: budowanie debug APK po zmianie kodu

## Budowanie APK
Wymagane JDK 17, Android SDK i Gradle 8.9 (Android Gradle Plugin 8.7.3). W Android Studio wybierz **Build > Build APK(s)**.
Po udanym przebiegu GitHub Actions plik `app-debug.apk` będzie w artefakcie `AstroProP40-debug`.

## Ważne ograniczenia
To prototyp aparatu, nie gotowa, przetestowana aplikacja astrofotograficzna. Nie ma jeszcze RAW/DNG, wyrównywania gwiazd ani stackowania, kalibracji dark/flat/bias ani edytora. Seria 10 ujęć automatyzuje kolejne zdjęcia, ale nie łączy ich w jeden obraz. Limity i dostępność ekspozycji zależą od Camera2 oraz firmware Huawei. Przed dystrybucją trzeba zbudować APK i sprawdzić działanie na fizycznym P40 Pro.
