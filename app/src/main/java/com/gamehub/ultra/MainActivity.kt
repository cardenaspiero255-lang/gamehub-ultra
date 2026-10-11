package com.gamehub.ultra

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gamehub.ultra.databinding.ActivityMainBinding
import com.gamehub.ultra.optimizer.HardwareDetector
import com.gamehub.ultra.optimizer.OptimizationProfile
import com.gamehub.ultra.optimizer.Optimizer

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val hardware = HardwareDetector.detect(this)
        val recommended = HardwareDetector.recommendedProfile(hardware)

        binding.deviceInfo.text =
            "${hardware.manufacturer} ${hardware.model}\n" +
            "${hardware.cpuCores} núcleos • ${hardware.ramGb} GB RAM • " +
            "${hardware.refreshRate.toInt()} Hz\n" +
            "Perfil recomendado: $recommended"

        binding.profileBalanced.isChecked = recommended == "BALANCED"
        binding.profilePerformance.isChecked = recommended == "PERFORMANCE"
        binding.profileCool.isChecked = recommended == "COOL"

        binding.optimizeButton.setOnClickListener {

            val selected = when (binding.profileGroup.checkedRadioButtonId) {
                binding.profileCool.id ->
                    OptimizationProfile.COOL

                binding.profilePerformance.id ->
                    OptimizationProfile.PERFORMANCE

                binding.profileX2.id ->
                    OptimizationProfile.INTERPOLATION_X2

                binding.profileX4.id ->
                    OptimizationProfile.INTERPOLATION_X4

                else ->
                    OptimizationProfile.BALANCED
            }

            val optimizer = Optimizer(this)
            binding.statusText.text = optimizer.apply(selected)
        }
    }
}
