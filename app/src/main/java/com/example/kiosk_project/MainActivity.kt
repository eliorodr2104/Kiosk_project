package com.example.kiosk_project

import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.kiosk_project.ui.theme.Kiosk_projectTheme
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.Exception
import java.lang.StringBuilder

/**
 * @author Eliomar Alejandro Rodriguez Ferrer
 * Classe MainActivity la quale implementa l'utilizzo della modalità "kiosk"
 */
class MainActivity : ComponentActivity() {
    private lateinit var mAdminComponentName: ComponentName
    private lateinit var mDevicePolicyManager: DevicePolicyManager

    private lateinit var fileInputStream: FileInputStream //Variabile per leggere il file della memoria di massa
    private lateinit var fileOutputStream: FileOutputStream //Variabile per scrivere il file nella memoria di massa

    private val REQUEST_ADMIN = 1

    private val regex = """^(https?|ftp)://[^\s/$.?#].\S*$""".toRegex() //Regex per l'indirizzo url, esempio per la regex: "https://google.com"

    /**
     * Funzione ereditata dalla classe ComponentActivity per creare la grafica
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mAdminComponentName = ComponentName(this, MyDeviceAdminReceiver::class.java)
        mDevicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager

        val dataRead = readDataFile() //Variavile della lettura dei dati

        // Verifica se l'app ha già i permessi di amministratore
        if (!mDevicePolicyManager.isAdminActive(mAdminComponentName)) {

            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN) //Chiede i permessi se non ci sono

            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mAdminComponentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Si chiedono per i permessi per poter bloccare certe funzionalità del tablet.")

            startActivityForResult(intent, REQUEST_ADMIN)
        } else {
            /*
             * If che controlla se esiste il il file nel dispositivo o se nel file c'è il link da cercare nella webview-
             * Se il file c'è apre direttamente la schermata del sito web, altrimenti apre la barra per cercare l'url.
             */
            if (dataRead == "ERROR" || dataRead == ""){
                setContent {
                    Kiosk_projectTheme {
                        InsertUrl()
                    }
                }

            }else{
                setContent {
                    Kiosk_projectTheme {
                        WebViewVisualizzazioneDaFile(infoDaFile = dataRead)
                    }
                }
            }
        }

        val isAdmin = isAdmin()

        setKioskPolicies(true, isAdmin)


    }

    private fun readDataFile(): String{
        return try {
            fileInputStream = openFileInput("dataFile.txt")

            val inputStream = InputStreamReader(fileInputStream)
            val bufferedReader = BufferedReader(inputStream)

            val stringBuilder = StringBuilder("")
            var text: String?

            while (run {
                    text = bufferedReader.readLine()
                    text

                } != null){
                stringBuilder.append(text)
            }

            stringBuilder.toString()
        }catch (e: Exception){
            "ERROR"
        }
    }

    private fun writeDataFile(textToWrite: String): Boolean{
        return try {
            fileOutputStream = openFileOutput("dataFile.txt", Context.MODE_PRIVATE)
            fileOutputStream.write(textToWrite.toByteArray())

            true
        }catch (e: Exception){
            false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ADMIN) {
            if (resultCode == RESULT_OK) {
                // L'utente ha accettato i permessi di amministratore
                setContent {
                    Kiosk_projectTheme {
                        WebViewScreen("")
                    }
                }
            } else {
                // L'utente ha rifiutato i permessi di amministratore
            }
        }
    }

    private fun isAdmin() = mDevicePolicyManager.isDeviceOwnerApp(packageName)

    private fun setKioskPolicies(enable: Boolean, isAdmin: Boolean) {
        if (isAdmin) {
            setRestrictions(enable)
            enableStayOnWhilePluggedIn(enable)
            setUpdatePolicy(enable)
            setAsHomeApp(enable)
            setKeyGuardEnabled(enable)
        }
        setLockTask(enable, isAdmin)
        setImmersiveMode(enable)
    }

    //region restrictions
    private fun setRestrictions(disallow: Boolean) {
        setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, disallow)
        setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, disallow)
        setUserRestriction(UserManager.DISALLOW_ADD_USER, disallow)
        setUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, disallow)
        setUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, disallow)
        mDevicePolicyManager.setStatusBarDisabled(mAdminComponentName, disallow)
    }

    private fun setUserRestriction(restriction: String, disallow: Boolean) = if (disallow) {
        mDevicePolicyManager.addUserRestriction(mAdminComponentName, restriction)
    } else {
        mDevicePolicyManager.clearUserRestriction(mAdminComponentName, restriction)
    }
    //endregion

    private fun enableStayOnWhilePluggedIn(active: Boolean) = if (active) {
        mDevicePolicyManager.setGlobalSetting(
            mAdminComponentName,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            (BatteryManager.BATTERY_PLUGGED_AC
                    or BatteryManager.BATTERY_PLUGGED_USB
                    or BatteryManager.BATTERY_PLUGGED_WIRELESS).toString()
        )
    } else {
        mDevicePolicyManager.setGlobalSetting(mAdminComponentName, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "0")
    }

    private fun setLockTask(start: Boolean, isAdmin: Boolean) {
        if (isAdmin) {
            mDevicePolicyManager.setLockTaskPackages(
                mAdminComponentName, if (start) arrayOf(packageName) else arrayOf()
            )
        }
        if (start) {
            startLockTask()
        } else {
            stopLockTask()
        }
    }

    private fun setUpdatePolicy(enable: Boolean) {
        if (enable) {
            mDevicePolicyManager.setSystemUpdatePolicy(
                mAdminComponentName,
                SystemUpdatePolicy.createWindowedInstallPolicy(60, 120)
            )
        } else {
            mDevicePolicyManager.setSystemUpdatePolicy(mAdminComponentName, null)
        }
    }

    private fun setAsHomeApp(enable: Boolean) {
        if (enable) {
            val intentFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            mDevicePolicyManager.addPersistentPreferredActivity(
                mAdminComponentName, intentFilter, ComponentName(packageName, MainActivity::class.java.name)
            )
        } else {
            mDevicePolicyManager.clearPackagePersistentPreferredActivities(
                mAdminComponentName, packageName
            )
        }
    }

    private fun setKeyGuardEnabled(enable: Boolean) {
        mDevicePolicyManager.setKeyguardDisabled(mAdminComponentName, !enable)
    }

    @Suppress("DEPRECATION")
    private fun setImmersiveMode(enable: Boolean) {
        if (enable) {
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.decorView.systemUiVisibility = flags
        } else {
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            window.decorView.systemUiVisibility = flags
        }
    }

    @Composable
    fun WebViewScreen(url: String) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val webView = WebView(context)
                setupWebView(webView)

                webView.loadUrl(url)

                webView
            }
        )
    }

    @Composable
    fun BackgroundUrl(){
        Text(
            text = "Benvenuto",
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier
                .padding(top = 69.dp, start = 17.dp)
        )
    }

    @Composable
    fun WebViewVisualizzazioneDaFile(infoDaFile: String){
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            WebViewScreen(infoDaFile)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Preview(device = Devices.AUTOMOTIVE_1024p, widthDp = 1280, heightDp = 800)
    @Composable
    fun InsertUrl() {
        val textState = remember { mutableStateOf("") }
        var errorState by remember { mutableStateOf(false) }

        val next = remember { mutableStateOf(false) }

        //!next.value
        if (!next.value){
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                BackgroundUrl()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(align = Alignment.Center)
                        .padding(top = 60.dp)
                        .padding(horizontal = 17.dp)
                ) {

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp)
                            .wrapContentSize(align = Alignment.Center),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    ) {
                        Text(
                            text = "Ciao! Inserisci il link della " +
                                    "pagina web che desideri mostrare in modalità \"kiosk\".",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            textAlign = TextAlign.Left,
                            modifier = Modifier
                                .padding(horizontal = 15.dp, vertical = 15.dp)
                        )

                        TextField(
                            value = textState.value,
                            onValueChange = { newText -> textState.value = newText },
                            label = { Text(
                                "Inserisci il link",
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            ) },
                            leadingIcon = {Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Gray
                            )},
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (regex.matches(textState.value)) {
                                    next.value = writeDataFile(textState.value)

                                    errorState = false

                                }else{
                                    errorState = true
                                }
                            }),
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = TextFieldDefaults.textFieldColors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            isError = errorState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp)

                        )

                        Button(onClick = {
                            if (regex.matches(textState.value)) {
                                next.value = writeDataFile(textState.value)

                                errorState = false

                            }else{
                                errorState = true
                            }

                        }, modifier = Modifier
                            .align(Alignment.End)
                            .padding(horizontal = 15.dp, vertical = 15.dp)) {


                            Text(
                                text = "Avanti"
                            )
                        }
                    }
                }
            }

        }else{
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                WebViewScreen(textState.value)
            }
        }
    }

    private fun setupWebView(webView: WebView) {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true // Abilita l'esecuzione di JavaScript
        webSettings.domStorageEnabled = true // Abilita lo storage del DOM

        // Imposta un WebViewClient per gestire le richieste e le risposte
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // L'intera pagina è stata caricata
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Gestisci l'apertura dei link all'interno del WebView stesso
                view?.loadUrl(request?.url.toString())
                return true
            }
        }
    }

}