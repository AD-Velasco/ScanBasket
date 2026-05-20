package com.advelasco.basketscanner

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.advelasco.basketscanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scanItem.setOnClickListener {
            val intent = Intent(this, ScanItemActivity::class.java)
            startActivity(intent)
        }

    }

}