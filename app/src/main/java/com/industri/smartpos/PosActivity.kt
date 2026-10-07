package com.industri.smartpos

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class PosActivity : AppCompatActivity() {
    private lateinit var etBarcode: EditText
    private lateinit var btnProcessPayment: Button
    private lateinit var pbPosLoading: ProgressBar
    private lateinit var cardPosResult: CardView
    private lateinit var tvPosStatus: TextView
    private lateinit var tvPosDetails: TextView
    private lateinit var rgSimulation: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pos)

        etBarcode = findViewById(R.id.etBarcode)
        btnProcessPayment = findViewById(R.id.btnProcessPayment)
        pbPosLoading = findViewById(R.id.pbPosLoading)
        cardPosResult = findViewById(R.id.cardPosResult)
        tvPosStatus = findViewById(R.id.tvPosStatus)
        tvPosDetails = findViewById(R.id.tvPosDetails)
        rgSimulation = findViewById(R.id.rgSimulation)

        btnProcessPayment.setOnClickListener {
            val barcode = etBarcode.text.toString().trim()
            if (barcode.isNotEmpty()) {
                eksekusiTransaksi(barcode)
            } else {
                Toast.makeText(this, "Barcode wajib diisi atau dipindai", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun eksekusiTransaksi(barcode: String) {
        pbPosLoading.visibility = View.VISIBLE
        cardPosResult.visibility = View.GONE
        btnProcessPayment.isEnabled = false

        // Coroutine Exception Handler: Pengaman terakhir agar aplikasi tidak force-close 
        val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
            Timber.e(exception, "FATAL COROUTINE ERROR TERTANGKAP: %s", exception.message)
            runOnUiThread {
                pbPosLoading.visibility = View.GONE
                btnProcessPayment.isEnabled = true
                tampilkanSnackbarError("Kesalahan sistem tak terduga: ${exception.localizedMessage}", canRetry = false)
            }
        }

        lifecycleScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            Timber.d("Memulai alur pembayaran untuk Barcode: %s", barcode)

            // Menggunakan idiom runCatching untuk menangani logika transaksi perbankan 
            val hasil = runCatching {
                delay(1200) // Simulasi latensi jaringan

                // Simulasi kondisi berdasarkan pilihan RadioButton
                when (rgSimulation.checkedRadioButtonId) {
                    R.id.rbBarcodeError -> {
                        throw ProductBarcodeNotFoundException(barcode) 
                    }
                    R.id.rbNetworkTimeout -> {
                        throw PaymentGatewayTimeoutException("QRIS Bank Settlement") 
                    }
                    else -> {
                        PosTransaction(
                            transactionId = "TRX-2026-9901",
                            barcode = barcode,
                            productName = "Susu UHT Full Cream 1 Liter",
                            totalAmount = 21500.0,
                            status = "SETTLED_SUCCESS"
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                pbPosLoading.visibility = View.GONE
                btnProcessPayment.isEnabled = true

                hasil.onSuccess { trx ->
                    Timber.i("Transaksi kasir berhasil dicatat: %s", trx.transactionId)
                    cardPosResult.visibility = View.VISIBLE
                    tvPosStatus.text = "TRANSAKSI BERHASIL (LUNAS)"
                    tvPosDetails.text = """
                        ID Transaksi : ${trx.transactionId}
                        Produk : ${trx.productName}
                        Total Bayar : Rp %,.2f
                        Waktu : Baru Saja
                    """.trimIndent()
                }.onFailure { err ->
                    Timber.w("Transaksi kasir ditolak / gagal: %s", err.message) 
                    when (err) {
                        is ProductBarcodeNotFoundException -> {
                            tampilkanSnackbarError(err.message ?: "Barcode tidak ditemukan", canRetry = false)
                        }
                        is PaymentGatewayTimeoutException -> {
                            // Error Jaringan: Berikan tombol aksi RETRY pada Snackbar
                            tampilkanSnackbarError(err.message ?: "Koneksi gateway terputus", canRetry = true)
                        }
                        else -> {
                            tampilkanSnackbarError("Kegagalan operasional: ${err.message}", canRetry = true)
                        }
                    }
                }
            }
        }
    }

    private fun tampilkanSnackbarError(pesan: String, canRetry: Boolean) {
        val root = findViewById<View>(R.id.coordinatorLayout)
        val snackbar = Snackbar.make(root, pesan, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(android.graphics.Color.parseColor("#991B1B")) // Merah gelap
        snackbar.setTextColor(android.graphics.Color.WHITE)

        if (canRetry) {
            snackbar.setAction("COBA LAGI") {
                val barcode = etBarcode.text.toString().trim()
                if (barcode.isNotEmpty()) eksekusiTransaksi(barcode)
            }
            snackbar.setActionTextColor(android.graphics.Color.parseColor("#FEF08A")) // Kuning
        }
        snackbar.show()
    }
}
