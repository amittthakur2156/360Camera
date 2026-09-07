package com.example.threesixtycamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerGallery: RecyclerView
    private lateinit var tvPhotoCount: TextView
    private lateinit var emptyState: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_gallery)

        recyclerGallery = findViewById(R.id.recyclerGallery)
        tvPhotoCount = findViewById(R.id.tvPhotoCount)
        emptyState = findViewById(R.id.emptyState)

        // Back button
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Camera button
        findViewById<View>(R.id.btnCamera).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        loadGallery()
    }

    private fun loadGallery() {

        val images = loadCapturedImages()

        if (images.isEmpty()) {

            tvPhotoCount.text = "No captured moments"

            emptyState.visibility = View.VISIBLE
            recyclerGallery.visibility = View.GONE

        } else {

            tvPhotoCount.text =
                "${images.size} captured moments"

            emptyState.visibility = View.GONE
            recyclerGallery.visibility = View.VISIBLE

            recyclerGallery.layoutManager =
                GridLayoutManager(this, 3)

            recyclerGallery.adapter =
                GalleryAdapter(images) { uri ->
                    openFullImage(uri)
                }
        }
    }

    private fun loadCapturedImages(): List<Uri> {

        val images = mutableListOf<Uri>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val selection =
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"

        val selectionArgs =
            arrayOf("IMG_%")

        val sortOrder =
            "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->

            val idColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media._ID
                )

            while (cursor.moveToNext()) {

                val id =
                    cursor.getLong(idColumn)

                val uri =
                    Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                images.add(uri)
            }
        }

        return images
    }

    private fun openFullImage(uri: Uri) {

        val intent = Intent(Intent.ACTION_VIEW).apply {

            setDataAndType(
                uri,
                "image/*"
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        startActivity(
            Intent.createChooser(
                intent,
                "View photo"
            )
        )
    }
}