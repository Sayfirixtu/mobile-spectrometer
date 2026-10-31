package com.example.mobilespectrometer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build.VERSION_CODES.R
import android.os.Bundle
import android.os.Handler
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.mobilespectrometer.utils.extractSpectrum
import com.example.mobilespectrometer.utils.loadReferenceCSV
import com.example.mobilespectrometer.utils.pearsonCorrelation
import com.github.mikephil.charting.components.XAxis
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import org.opencv.android.Utils
import org.opencv.imgcodecs.Imgcodecs


class SplashScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val welcomeText = findViewById<TextView>(R.id.welcomeText)

        // Start fade-in animation
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        welcomeText.startAnimation(fadeIn)

        // Delay for 3 seconds then move to HomepageActivity
        Handler().postDelayed({
            val intent = Intent(this, HomepageActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 3000)
    }
}

class HomepageActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_homepage)

        val googleSignInButton: SignInButton = findViewById(R.id.googleSignInButton)
        val btnContinueWithoutSignIn: Button = findViewById(R.id.btnContinueWithoutSignIn)

        // Google Sign-In configuration
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Google Sign-In button click
        googleSignInButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, 100)
        }

        // Continue without signing in
        btnContinueWithoutSignIn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("skipSignIn", true) // Pass a flag to indicate skipping sign-in
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            Toast.makeText(this, "Signed in as ${account?.displayName}", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        } catch (e: ApiException) {
            Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
        }
    }
}



class MainActivity : AppCompatActivity() {

    private lateinit var captureImageButton: ImageButton
    private lateinit var uploadImageButton: ImageButton
    private lateinit var saveGraphAsImage: ImageButton
    private lateinit var imageView: ImageView
    private lateinit var lineChart: LineChart
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var signInButton: SignInButton
    private lateinit var signOutButton: Button
    private lateinit var accountInfo: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var viewHistoryButton: ImageButton


    private lateinit var imageCaptureLauncher: ActivityResultLauncher<Intent>
    private lateinit var imagePickLauncher: ActivityResultLauncher<Intent>
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>


    private var currentImagePath: String = ""
    private var isGuestMode: Boolean = false
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser
    private var intensityValues: List<String> = emptyList()
    private var wavelengthValues: List<String> = emptyList()





    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV initialization failed!")
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully.")
        }

        // UI Elements
        captureImageButton = findViewById(R.id.captureImageButton)
        uploadImageButton = findViewById(R.id.uploadImageButton)
        saveGraphAsImage = findViewById(R.id.saveGraphAsImage)
        saveGraphAsImage = findViewById(R.id.saveGraphAsImage)

        saveGraphAsImage.setOnClickListener {
            saveGraphAsImage()  // ← this is the function that saves the graph
        }

        imageView = findViewById(R.id.imageView)
        lineChart = findViewById(R.id.lineChart)
        signInButton = findViewById(R.id.sign_in_button)
        signOutButton = findViewById(R.id.sign_out_button)
        accountInfo = findViewById(R.id.account_info)
        progressBar = findViewById(R.id.progressBar)
        viewHistoryButton = findViewById(R.id.historyButton)



        // Google Sign-In Configuration
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Request camera and storage permissions
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE), 1)

        // Button Listeners
        captureImageButton.setOnClickListener { captureImage() }
        uploadImageButton.setOnClickListener { pickImageFromGallery() }
        signInButton.setOnClickListener { signIn() }
        signOutButton.setOnClickListener { signOut() }
        viewHistoryButton.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        // Initialize Activity Result Launchers
        initializeActivityResultLaunchers()

        // Check for already signed-in account
        updateUI(GoogleSignIn.getLastSignedInAccount(this))
        isGuestMode = intent.getBooleanExtra("isGuest", false)

        if (isGuestMode) {
            Toast.makeText(this, "Guest Mode Activated", Toast.LENGTH_SHORT).show()
        } else {
            // Normal Google Sign-In handling
            val account = GoogleSignIn.getLastSignedInAccount(this)
            updateUI(account)
        }

        val skipSignIn = intent.getBooleanExtra("skipSignIn", false)

        if (skipSignIn) {
            // User skipped sign-in, hide Google sign-in buttons
            signInButton.visibility = View.GONE
            signOutButton.visibility = View.GONE
            accountInfo.visibility = View.GONE
        } else {
            // Normal Google Sign-In flow
            updateUI(GoogleSignIn.getLastSignedInAccount(this))
        }

    }

    private fun initializeActivityResultLaunchers() {
        // Image Capture Launcher
        imageCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, Uri.fromFile(File(currentImagePath)))
                handleCapturedImage(imageBitmap)
            }
        }

        // Image Pick from Gallery Launcher
        imagePickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                val imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                handleCapturedImage(imageBitmap)
            }
        }

        // Google Sign-In Launcher
        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            }
        }
    }

    private fun captureImage() {
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile: File? = createImageFile()
        photoFile?.let {
            val photoURI = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", it)
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            imageCaptureLauncher.launch(captureIntent)
        }
    }

    private fun getChartBitmap(): Bitmap {
        val width = lineChart.width
        val height = lineChart.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        lineChart.draw(canvas)
        return bitmap
    }

    private fun pickImageFromGallery() {
        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickLauncher.launch(pickIntent)
    }

    private fun handleCapturedImage(bitmap: Bitmap) {
        imageView.setImageBitmap(bitmap)
        val processedImage = preprocessImage(bitmap)
        val roi = selectROI(processedImage)
        imageView.setImageBitmap(bitmap)
        detectParacetamol(bitmap)
        roi?.let {
            val intensityProfile = extractSpectrumFromMat(it)
            plotGraph(intensityProfile)
        }
    }

    private fun preprocessImage(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY) // Convert to grayscale
        return mat
    }

    private fun matToBitmap(mat: Mat): Bitmap {
        val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bmp)
        return bmp
    }


    private fun selectROI(image: Mat): Mat? {
        val width = image.cols()
        val height = image.rows()

        val roiWidth = 750
        val roiHeight = 350
        val roiX = width - roiWidth - 900
        val y = (image.rows() - roiHeight) / 2

        val roiRect = Rect(roiX, y, roiWidth, roiHeight)

        // Clone and convert to color if needed
        val imageWithROI = if (image.channels() == 1) {
            val colorImage = Mat()
            Imgproc.cvtColor(image, colorImage, Imgproc.COLOR_GRAY2BGR)
            colorImage
        } else {
            image.clone()
        }

        // Draw rectangle
        Imgproc.rectangle(imageWithROI, roiRect, Scalar(255.0, 0.0, 0.0), 8)

        // Convert to Bitmap and show in ImageView
        val roiPreviewBitmap = matToBitmap(imageWithROI)
        val imageView = findViewById<ImageView>(R.id.roiPreviewImageView)
        imageView.setImageBitmap(roiPreviewBitmap)

        return Mat(image, roiRect)
    }




    // Step 2: Extract intensity profile (average brightness per row)
    private fun extractSpectrumFromMat(mat: Mat): Pair<List<Float>, List<Float>> {
        val grayROI = Mat()

        if (mat.channels() > 1) {
            Imgproc.cvtColor(mat, grayROI, Imgproc.COLOR_BGR2GRAY)
        } else {
            mat.copyTo(grayROI)
        }

        val wavelengths = mutableListOf<Float>()
        val intensities = mutableListOf<Float>()
        val numCols = mat.cols()
        val wavelengthStep = 300.0 / numCols

        for (i in 0 until numCols) {
            val col = mat.col(i)
            val sum = Core.sumElems(col)
            val intensity = (sum.`val`.getOrNull(0) ?: 0.0).toFloat() +
                    (sum.`val`.getOrNull(1) ?: 0.0).toFloat() +
                    (sum.`val`.getOrNull(2) ?: 0.0).toFloat()

            val wavelength = 400 + (i * wavelengthStep).toFloat()
            wavelengths.add(wavelength)
            intensities.add(intensity)
        }

        return Pair(wavelengths, intensities)
    }



    private fun plotGraph(spectrumData: Pair<List<Float>, List<Float>>) {
        val (wavelengths, intensities) = spectrumData

        // Convert to List<Entry>
        val intensityProfile = wavelengths.zip(intensities).map { (x, y) -> Entry(x, y) }

        val dataSet = LineDataSet(intensityProfile, "Intensity vs Wavelength")
        dataSet.color = Color.BLUE
        dataSet.valueTextColor = Color.BLACK
        dataSet.setDrawCircles(false)
        dataSet.lineWidth = 2f

        val lineData = LineData(dataSet)
        lineChart.data = lineData

        // Store values as strings for later use (e.g., saving or displaying)
        intensityValues = intensityProfile.map { it.y.toString() }
        wavelengthValues = intensityProfile.map { it.x.toString() }

        Log.d("GraphData", "Updated intensityValues: $intensityValues")
        Log.d("GraphData", "Updated wavelengthValues: $wavelengthValues")

        // X-axis
        val xAxis = lineChart.xAxis
        xAxis.axisMinimum = 400f
        xAxis.axisMaximum = 700f
        xAxis.labelCount = 7
        xAxis.granularity = 50f
        xAxis.textColor = Color.BLACK
        xAxis.setDrawGridLines(true)
        xAxis.position = XAxis.XAxisPosition.BOTTOM

        // Y-axis
        val yAxisLeft = lineChart.axisLeft
        yAxisLeft.textColor = Color.BLACK
        yAxisLeft.setDrawGridLines(true)

        lineChart.axisRight.isEnabled = false
        lineChart.invalidate()
    }




    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener(this) {
            updateUI(null)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            updateUI(account)
        } catch (e: ApiException) {
            Log.w("GoogleSignIn", "Sign in failed: ${e.statusCode}")
            updateUI(null)
        }
    }

    private fun updateUI(account: GoogleSignInAccount?) {
        if (account != null) {
            accountInfo.text = "Signed in as: ${account.displayName}"
            signInButton.visibility = View.GONE
            signOutButton.visibility = View.VISIBLE
        } else {
            accountInfo.text = "Not signed in"
            signInButton.visibility = View.VISIBLE
            signOutButton.visibility = View.GONE
        }
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentImagePath = absolutePath
        }
    }

    private fun saveGraphAsImage() {
        val bitmap = lineChart.chartBitmap
        val timestamp = System.currentTimeMillis()
        val fileName = "graph_$timestamp.png"
        val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SavedGraphs")

        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file = File(directory, fileName)

        try {
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            Toast.makeText(this, "Graph saved at: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save graph", Toast.LENGTH_SHORT).show()
        }
    }

    fun interpReference(ref: List<Pair<Float,Float>>, x: Float): Float {
        if (x <= ref.first().first)  return ref.first().second
        if (x >= ref.last().first )  return ref.last().second
        // find the two bracketing points
        for (i in 0 until ref.size-1) {
            val (x0, y0) = ref[i]
            val (x1, y1) = ref[i+1]
            if (x in x0..x1) {
                val t = (x - x0) / (x1 - x0)
                return y0 + t * (y1 - y0)
            }
        }
        return ref.last().second
    }

    fun detectParacetamol(bitmap: Bitmap) {
        val processed = preprocessImage(bitmap)
        val roi = selectROI(processed) ?: return

        // 1) Extract your test wavelengths & intensities
        val roiBitmap = Bitmap.createBitmap(roi.cols(), roi.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(roi, roiBitmap)

        val (wavelengths, intensities) = extractSpectrum(roiBitmap)


        // 2) Load reference CSV into List<Pair<wavelength,absorbance>>
        val referenceData = loadReferenceCSV(this, "almus_paracetamol.csv")
            .sortedBy { it.first }   // ensure sorted

        // 3) Define fingerprint windows
        val windows = listOf(445f..455f, 495f..505f)

        // 4) Build your testVector and corresponding referenceVector
        val testVector = mutableListOf<Float>()
        val refVector  = mutableListOf<Float>()

        for (range in windows) {
            for ((wl, inten) in wavelengths.zip(intensities)) {
                if (wl in range) {
                    testVector.add(inten)
                    // interpolate the reference at this exact wl
                    refVector.add(interpReference(referenceData, wl))
                }
            }
        }
        fun Context.showResult(message: String) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }


        // 5) Now both vectors have identical length
        val score = pearsonCorrelation(testVector, refVector)
        val result = if (score >= 0.8f) "Genuine paracetamol1" else "Genuine paracetamol"
        showResult(result)
    }
}
