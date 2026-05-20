package com.advelasco.basketscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.database.FirebaseDatabase
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanItemActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var scanResult: TextView
    private lateinit var productName: TextView
    private lateinit var productPrice: TextView
    private lateinit var btnCancel: ExtendedFloatingActionButton
    private lateinit var btnAdd: ExtendedFloatingActionButton
    private lateinit var cameraExecutor: ExecutorService
    private val CAMERA_PERMISSION_CODE = 101

    private var lastScannedBarcode: String = ""
    private var productFound: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_item)

        previewView = findViewById(R.id.previewView)
        scanResult = findViewById(R.id.scanResult)
        productName = findViewById(R.id.productName)
        productPrice = findViewById(R.id.productPrice)
        btnCancel = findViewById(R.id.btnCancel)
        btnAdd = findViewById(R.id.btnAdd)
        cameraExecutor = Executors.newSingleThreadExecutor()

        btnCancel.setOnClickListener {
            lastScannedBarcode = ""
            productFound = false
            scanResult.text = "Point camera at a barcode"
            productName.text = ""
            productPrice.text = ""
        }

        btnAdd.setOnClickListener {
            if(!productFound){
                showAddProductDialog(lastScannedBarcode)
            }else{
                Toast.makeText(this, "Added to cart!", Toast.LENGTH_SHORT).show()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    private fun showAddProductDialog(barcode: String){
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_product, null)
        val inputName = dialogView.findViewById<EditText>(R.id.inputProductName)
        val inputPrice = dialogView.findViewById<EditText>(R.id.inputProductPrice)

        AlertDialog.Builder(this)
            .setTitle("Add New Product")
            .setMessage("Barcode: $barcode")
            .setView(dialogView)
            .setPositiveButton("Save"){_, _ ->
                val name = inputName.text.toString().trim()
                val price = inputPrice.text.toString().trim().toDoubleOrNull()

                if(name.isEmpty() || price == null){
                    Toast.makeText(this, "Please enter valid name and price", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val database = FirebaseDatabase.getInstance("https://basket-scanner-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("products")
                val newProduct = mapOf("name"  to name, "price" to price)
                database.child(barcode).setValue(newProduct)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Product saved!", Toast.LENGTH_SHORT).show()
                        productName.text = name
                        productPrice.text = "₱%.2f".format(price)
                        scanResult.text = "Product added!"
                        productFound = true
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to save product", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    // Single clean lookup function
    fun lookupBarcode(barcode: String) {

        // Skip if same barcode is scanned repeatedly
        if (barcode == lastScannedBarcode) return
        lastScannedBarcode = barcode

        runOnUiThread {
            scanResult.text = "Scanning: $barcode"
            productName.text = ""
            productPrice.text = ""
            btnAdd.isEnabled = false
            btnAdd.alpha = 0.5f
        }

        val database = FirebaseDatabase.getInstance("https://basket-scanner-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("products")
        database.child(barcode).get().addOnSuccessListener {
            if (it.exists()) {
                val name = it.child("name").value.toString()
                val price = it.child("price").value.toString().toDouble()
                runOnUiThread {
                    scanResult.text = "$barcode"
                    productName.text = name
                    productPrice.text = "₱%.2f".format(price)
                    btnAdd.isEnabled = false
                    btnAdd.alpha = 0.5f
                }
            } else {
                runOnUiThread {
                    scanResult.text = "Product not found: $barcode"
                    productName.text = ""
                    productPrice.text = ""
                    btnAdd.isEnabled = true
                    btnAdd.alpha = 1.0f
                }
            }
        }.addOnFailureListener {
            runOnUiThread {
                scanResult.text = "Failed to reach database"
                productName.text = ""
                productPrice.text = ""
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        // onResult callback brings barcode back to ScanItemActivity
                        // so lookupBarcode() can be called here safely
                        lookupBarcode(barcode)
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

private class BarcodeAnalyzer(val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val value = barcode.rawValue ?: return@addOnSuccessListener
                        onResult(value)  // ← just passes value back, nothing else
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}