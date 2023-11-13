package com.example.evaluacion_3

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.time.LocalDateTime
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint
import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import coil.compose.rememberImagePainter

class MainActivity : ComponentActivity() {
    val cameraAppVm:CameraAppViewModel by viewModels()
    lateinit var cameraController:LifecycleCameraController
    val lanzadorPermisos = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            when {
                (it[Manifest.permission.ACCESS_FINE_LOCATION] ?:
                false) or (it[Manifest.permission.ACCESS_COARSE_LOCATION] ?:
                false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso ubicacion granted")
                            cameraAppVm.onPermisoUbicacionOk()
                }
                (it[Manifest.permission.CAMERA] ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso camara granted")
                            cameraAppVm.onPermisoCamaraOk()
                }
                else -> {
                }
            }
        }
    private fun setupCamara() {
        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraAppVm.lanzadorPermisos = lanzadorPermisos
        setupCamara()
        setContent {
            AppUI(cameraController)
        }
    }
}

enum class Pantalla {
    FORM,
    FOTO
}

class CameraAppViewModel : ViewModel() {
    val pantalla = mutableStateOf(Pantalla.FORM)
    var onPermisoCamaraOk : () -> Unit = {}
    var onPermisoUbicacionOk: () -> Unit = {}
    var lanzadorPermisos: ActivityResultLauncher<Array<String>>? = null
    fun cambiarPantallaFoto(){ pantalla.value = Pantalla.FOTO }
    fun cambiarPantallaForm(){ pantalla.value = Pantalla.FORM }
}

class FormRecepcionViewModel : ViewModel() {
    val receptor = mutableStateOf("")
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)
    val fotosRecepcion = mutableStateListOf<Uri>()
    fun agregarFoto(uri: Uri) {
        fotosRecepcion.add(uri)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun generarNombre():String = LocalDateTime
    .now().toString().replace(Regex("[T:.-]"), "").substring(0, 14)

@RequiresApi(Build.VERSION_CODES.O)
fun crearArchivoImagenPrivado(contexto: Context): File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${generarNombre()}.jpg"
)


fun uri2imageBitmap(uri: Uri, contexto: Context): ImageBitmap {
    val bitmap = BitmapFactory.decodeStream(contexto.contentResolver.openInputStream(uri))
    val rotatedBitmap = rotateBitmap(bitmap, 90f)
    return rotatedBitmap.asImageBitmap()
}


fun tomarFotografia(cameraController: CameraController, archivo:File,
                    contexto:Context, imagenGuardadaOk:(uri:Uri)->Unit) {
    val outputFileOptions = ImageCapture.OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(outputFileOptions,
        ContextCompat.getMainExecutor(contexto), object: ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults:
                                      ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.also {
                    Log.v("tomarFotografia()::onImageSaved", "Foto guardada en ${it.toString()}")
                            imagenGuardadaOk(it)
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("tomarFotografia()", "Error: ${exception.message}")
            }
        })
}

class SinPermisoException(mensaje:String) : Exception(mensaje)

fun getUbicacion(contexto: Context, onUbicacionOk:(location: Location) ->
Unit):Unit {
    try {
        val servicio =
            LocationServices.getFusedLocationProviderClient(contexto)
        val tarea =
            servicio.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        tarea.addOnSuccessListener {
            onUbicacionOk(it)
        }
    } catch (e:SecurityException) {
        throw SinPermisoException(e.message?:"No tiene permisos para conseguir la ubicación")
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppUI(cameraController: CameraController) {
    val contexto = LocalContext.current
    val formRecepcionVm:FormRecepcionViewModel = viewModel()
    val cameraAppViewModel:CameraAppViewModel = viewModel()
    when(cameraAppViewModel.pantalla.value) {
        Pantalla.FORM -> {
            PantallaFormUI(
                formRecepcionVm,
                tomarFotoOnClick = {
                    cameraAppViewModel.cambiarPantallaFoto()
                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(Manifest.permission.CAMERA))
                },
                actualizarUbicacionOnClick = {
                    cameraAppViewModel.onPermisoUbicacionOk = {
                        getUbicacion(contexto) {
                            formRecepcionVm.latitud.value = it.latitude
                            formRecepcionVm.longitud.value = it.longitude
                        }
                    }
                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            )
        }
        Pantalla.FOTO -> {
            PantallaFotoUI(formRecepcionVm, cameraAppViewModel,
                cameraController)
        }
        else -> {
            Log.v("AppUI()", "when else, no debería entrar aquí")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaFormUI(
    formRecepcionVm:FormRecepcionViewModel,
    tomarFotoOnClick:() -> Unit = {},
    actualizarUbicacionOnClick:() -> Unit = {}
) {
    val contexto = LocalContext.current
    val showDialog = remember { mutableStateOf(false) }
    val selectedImage = remember { mutableStateOf<Uri?>(null) }
    val showMapDialog = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                label = { Text("Lugar") },
                value = formRecepcionVm.receptor.value,
                onValueChange = { formRecepcionVm.receptor.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
            )
            Text("Fotografía del lugar donde se encuentra:")
            Button(onClick = {
                tomarFotoOnClick()
            }) {
                Text("Tomar Fotografía")
            }
            formRecepcionVm.fotosRecepcion.forEach { uri ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.size(100.dp, 200.dp)) {
                    Image(
                        painter = BitmapPainter(uri2imageBitmap(uri, contexto)),
                        contentDescription = "Imagen lugar ${formRecepcionVm.receptor.value}",
                        modifier = Modifier.clickable {
                            selectedImage.value = uri
                            showDialog.value = true
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            if (showDialog.value) {
                AlertDialog(
                    onDismissRequest = {
                        showDialog.value = false
                        selectedImage.value = null
                    },
                    title = { Text("Imagen Completa") },
                    text = {
                        selectedImage.value?.let { uri ->
                            Image(
                                painter = rememberImagePainter(uri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(500.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDialog.value = false
                                selectedImage.value = null
                            }
                        ) {
                            Text("Cerrar")
                        }
                    }
                )
            }

            Text("La ubicación es: lat: ${formRecepcionVm.latitud.value} y long: ${formRecepcionVm.longitud.value}")
            Button(onClick = {
                actualizarUbicacionOnClick()
                showMapDialog.value = true
            }) {
                Text("Ver Ubicación")
            }
            Spacer(Modifier.height(20.dp))
            if (showMapDialog.value) {
                Dialog(
                    onDismissRequest = {
                        showMapDialog.value = false
                    },
                    content = {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MapaOsmUI(
                                formRecepcionVm.latitud.value,
                                formRecepcionVm.longitud.value,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    showMapDialog.value = false
                                }
                            ) {
                                Text("Cerrar")
                            }
                        }
                    }
                )
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PantallaFotoUI(formRecepcionVm:FormRecepcionViewModel, appViewModel:
CameraAppViewModel, cameraController: CameraController) {
    val contexto = LocalContext.current
    AndroidView(
        factory = {
            PreviewView(it).apply {
                controller = cameraController
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    Button(onClick = {
        tomarFotografia(
            cameraController,
            crearArchivoImagenPrivado(contexto),
            contexto
        ) {
            formRecepcionVm.agregarFoto(it)
            appViewModel.cambiarPantallaForm()
        }
    }) {
        Text("Tomar foto")
    }
}

@Composable
fun MapaOsmUI(latitud:Double, longitud:Double, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    AndroidView(
        factory = {
            MapView(it).also {
                it.setTileSource(TileSourceFactory.MAPNIK)
                Configuration.getInstance().userAgentValue = contexto.packageName
            }
        },
        modifier = modifier,
        update = {
            it.overlays.removeIf { true }
            it.invalidate()
            it.controller.setZoom(18.0)
            val geoPoint = GeoPoint(latitud, longitud)
            it.controller.animateTo(geoPoint)
            val marcador = Marker(it)
            marcador.position = geoPoint
            marcador.setAnchor(Marker.ANCHOR_CENTER,
                Marker.ANCHOR_CENTER)
            it.overlays.add(marcador)
        }
    )
}

fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
