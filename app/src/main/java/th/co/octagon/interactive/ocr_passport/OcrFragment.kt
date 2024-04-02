package th.co.octagon.interactive.ocr_passport

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import th.co.octagon.interactive.ocr_passport.databinding.FragmentOcrBinding
import th.co.octagon.interactive.ocr_passport.model.PassportModel
import java.util.concurrent.Executors

class OcrFragment : Fragment() {

    companion object {
        fun newInstance() = OcrFragment()

        private val TAG = OcrFragment::class.java.simpleName
        const val PERMISSION_CAMERA_REQUEST = 1
    }

    private var _binding: FragmentOcrBinding? = null
    private val binding get() = _binding!!

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelectorBack: CameraSelector? = null
    private var cameraSelectorFront: CameraSelector? = null
    private var cameraAvailable: Int? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null

    private lateinit var recognizer: TextRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOcrBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupCamera()

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


    }

    private fun setupCamera() {
        cameraSelectorBack = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        cameraSelectorFront = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        ViewModelProvider(
            this, ViewModelProvider.AndroidViewModelFactory.getInstance(application = requireContext().applicationContext as Application)
        )[CameraXViewModel::class.java]
            .processCameraProvider
            .observe(viewLifecycleOwner) { provider: ProcessCameraProvider? ->
                cameraProvider = provider
                if (isCameraPermissionGranted()) {
                    bindCameraUseCases()
                } else {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(Manifest.permission.CAMERA),
                        PERMISSION_CAMERA_REQUEST
                    )
                }
            }

    }

    private fun bindCameraUseCases() {
        bindPreviewUseCase()
        bindAnalyseUseCase()
    }

    private fun bindPreviewUseCase() {
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }

        previewUseCase = Preview.Builder()
            .setTargetRotation(binding.previewView.display.rotation)
            .build()

        previewUseCase!!.setSurfaceProvider(binding.previewView.surfaceProvider)


        if (!tryBackCamera()) {
            tryFrontCamera()
        }

    }

    private fun tryBackCamera(): Boolean {
        var result = true
        try {
            cameraProvider!!.bindToLifecycle(
                this,
                cameraSelectorBack!!,
                previewUseCase
            )
            cameraAvailable = 1
            //AppConfigStorage(mContext).setCameraAvailable(1)
        } catch (illegalStateException: IllegalStateException) {
            Log.e(TAG, illegalStateException.message ?: "IllegalStateException")
            result = false
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(TAG, illegalArgumentException.message ?: "IllegalArgumentException")
            result = false
        }

        return result
    }

    private fun tryFrontCamera(): Boolean {
        var result = true
        try {
            cameraProvider!!.bindToLifecycle(
                this,
                cameraSelectorFront!!,
                previewUseCase
            )
            cameraAvailable = 2
            //AppConfigStorage(mContext).setCameraAvailable(2)
        } catch (illegalStateException: IllegalStateException) {
            Log.e(TAG, illegalStateException.message ?: "IllegalStateException")
            result = false
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(TAG, illegalArgumentException.message ?: "IllegalArgumentException")
            result = false
        }

        return result
    }

    private fun bindAnalyseUseCase() {
        val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()

        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(options)

        if (cameraProvider == null) {
            return
        }

        if (analysisUseCase != null) {
            cameraProvider!!.unbind(analysisUseCase)
        }

        analysisUseCase = ImageAnalysis.Builder()
            .setTargetRotation(binding.previewView.display.rotation)
            .build()

        // Initialize our background executor
        val cameraExecutor = Executors.newSingleThreadExecutor()

        analysisUseCase?.setAnalyzer(
            cameraExecutor
        ) { imageProxy ->
            analyzeImage(imageProxy)
        }

        try {
            if (cameraAvailable == 1) {
                cameraProvider!!.bindToLifecycle(this, cameraSelectorBack!!, analysisUseCase)
            } else {
                cameraProvider!!.bindToLifecycle(this, cameraSelectorFront!!, analysisUseCase)
            }
        } catch (illegalStateException: IllegalStateException) {
            Log.e("illegalStateException", illegalStateException.message ?: "IllegalStateException")
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e("illegalStateException", illegalArgumentException.message ?: "IllegalArgumentException")
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeImage(image: ImageProxy) {
        // Create an InputImage object from the ImageProxy
        val inputImage = InputImage.fromMediaImage(image.image!!, image.imageInfo.rotationDegrees)

        // Get an instance of the text recognizer using the default options
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // Process the image with the text recognizer
        val result = recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                // Task completed successfully
                // Handle the text recognition results here
                // You can access the recognized text using visionText.text
                val recognizedText = visionText.text

                val lines = recognizedText.split("\n")

                for (line in lines) {
//                    if (line.startsWith("P<")) {
//                        println("line1 ${line.trim()}")
//                    }
                    val trimmedString = line.replace("\\s+".toRegex(), "")

                    if (trimmedString.takeLast(4).startsWith("<<") && trimmedString.length == 44) {
                        val lastTwoCharacters = line.takeLast(2)

                        try {
                            val lastTwoAsInt = lastTwoCharacters.toInt()
                            println("lastTwoCharacters เป็น Int")

                            mrzFormat(trimmedString)
                        } catch (e: NumberFormatException) {

                        }
                    }
                }

                image.close()
            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                Log.e(TAG, "Text recognition failed: ${e.message}", e)
                // Handle the failure appropriately
                // It's important to close the image proxy even if processing failed
                image.close()
            }
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_CAMERA_REQUEST) {
            if (isCameraPermissionGranted()) {
                bindCameraUseCases()
            } else {
                Log.e(TAG, "no camera permission")
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun mrzFormat(mrz: String) {
        val mrzLength = mrz.length

        var englishLetterIndex = -1
        for (i in 9 until mrzLength) {
            if (mrz[i].isLetter() && mrz[i].isUpperCase()) {
                englishLetterIndex = i
                break
            }
        }

        if (englishLetterIndex == -1) {
            englishLetterIndex = mrzLength
        }

        val documentNumber = mrz.substring(0, englishLetterIndex)

        val line2Split = mrz.split(documentNumber)
        if (line2Split.size > 1) {
            println("true ${line2Split[1]}")
            val firstThreeChars = line2Split[1].substring(0, 3)

            if (firstThreeChars.all { it.isLetter() }) {
                println("Document Number: $documentNumber")

                val firstData = line2Split[1].substring(3, 9)
                val secondData = line2Split[1].substring(11, 17)

                try {
                    val dataNumber1 = firstData.toInt()
                    val dataNumber2 = secondData.toInt()

                    println("First data: $dataNumber1")
                    println("Second data: $dataNumber2")

                    stopCamera()

                    val passportModel = PassportModel(
                        documentNumber = documentNumber,
                        birthDate = dataNumber1,
                        expiryDate = dataNumber2
                    )

                    val gson: Gson = GsonBuilder().setPrettyPrinting().create()
                    val action = OcrFragmentDirections.actionOcrFragmentToMainFragment(gson.toJson(passportModel))
                    findNavController().navigate(action)
                } catch (e: Exception) {

                }
            }
        }
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraSelectorBack = null
        cameraSelectorFront = null
        cameraAvailable = null
        previewUseCase = null
        analysisUseCase = null
    }
}