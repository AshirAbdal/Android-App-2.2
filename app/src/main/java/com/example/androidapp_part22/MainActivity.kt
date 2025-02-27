package com.example.androidapp_part22

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var voiceInput: EditText
    private lateinit var micButton: Button
    private lateinit var clearButton: Button
    private lateinit var settingsButton: Button
    private lateinit var showEntriesButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val idleTimeout = 10000L
    private val voiceFileName = "voice.txt"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceRecognition()
        } else {
            Toast.makeText(this, "Permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupListeners()
        applyPreferences()
        startIdleTimer()
    }

    private fun initializeViews() {
        voiceInput = findViewById(R.id.voiceInput)
        micButton = findViewById(R.id.micButton)
        clearButton = findViewById(R.id.clearButton)
        settingsButton = findViewById(R.id.settingsButton)
        showEntriesButton = findViewById(R.id.showEntriesButton)
    }

    private fun setupListeners() {
        micButton.setOnClickListener { checkAudioPermission() }
        clearButton.setOnClickListener { clearText() }
        settingsButton.setOnClickListener { openSettings() }
        showEntriesButton.setOnClickListener { showSavedEntries() }
        voiceInput.setOnKeyListener { _, _, _ -> resetIdleTimer(); false }
    }

    private fun checkAudioPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> startVoiceRecognition()

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) -> Toast.makeText(this, "Microphone access needed for voice input", Toast.LENGTH_LONG).show()

            else -> requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceRecognition() {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, getPreferredLanguage())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
            try {
                startActivityForResult(this, REQUEST_CODE_SPEECH_INPUT)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                voiceInput.setText(it)
                saveVoiceEntry(it)
                resetIdleTimer()
            }
        }
    }

    private fun saveVoiceEntry(text: String) {
        try {
            val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
            val count = prefs.getInt("voice_entry_count", 0) + 1

            File(filesDir, voiceFileName).appendText("voice $count text: $text\n")

            prefs.edit().putInt("voice_entry_count", count).apply()
            Toast.makeText(this, "Auto-saved: Entry $count", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSavedEntries() {
        val file = File(filesDir, voiceFileName)
        if (file.exists()) {
            val content = file.readText().ifEmpty { "No entries found" }
            AlertDialog.Builder(this)
                .setTitle("Saved Entries")
                .setMessage(content)
                .setPositiveButton("OK", null)
                .show()
        } else {
            Toast.makeText(this, "No entries found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearText() {
        voiceInput.text.clear()
        Toast.makeText(this, "Text cleared", Toast.LENGTH_SHORT).show()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        when (prefs.getString("theme", "Light")) {
            "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun applyPreferences() {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        with(voiceInput) {
            textSize = prefs.getFloat("textSize", 18f)
            typeface = when (prefs.getString("fontStyle", "Normal")) {
                "Bold" -> Typeface.DEFAULT_BOLD
                "Italic" -> Typeface.defaultFromStyle(Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }
        }
    }

    private fun startIdleTimer() = handler.postDelayed(::saveCurrentText, idleTimeout)
    private fun resetIdleTimer() = handler.apply {
        removeCallbacksAndMessages(null)
        postDelayed(::saveCurrentText, idleTimeout)
    }

    private fun saveCurrentText() {
        voiceInput.text.takeIf { it.isNotEmpty() }?.toString()?.let {
            saveVoiceEntry(it)
        }
    }

    private fun getPreferredLanguage(): String {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        return prefs.getString("language", Locale.getDefault().language) ?: "en"
    }

    companion object {
        private const val REQUEST_CODE_SPEECH_INPUT = 100
    }
}