package com.jumbox.app.muslim.ui.spalsh

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.github.razir.progressbutton.DrawableButton
import com.github.razir.progressbutton.hideProgress
import com.github.razir.progressbutton.showProgress
import com.jumbox.app.muslim.R
import com.jumbox.app.muslim.base.BaseActivity
import com.jumbox.app.muslim.data.pref.Preference
import com.jumbox.app.muslim.databinding.ActivityStartInitializeBinding
import com.jumbox.app.muslim.receiver.ReminderReceiver
import com.jumbox.app.muslim.service.LocationServices
import com.jumbox.app.muslim.ui.main.BottomSheetFindRegion
import com.jumbox.app.muslim.ui.main.MainActivity
import com.jumbox.app.muslim.ui.main.MainViewModel
import com.jumbox.app.muslim.vo.Status
import com.jumbox.app.muslim.vo.Suggestion
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*
import javax.inject.Inject

class StartInitializeActivity : BaseActivity<ActivityStartInitializeBinding, MainViewModel>() ,
    MultiplePermissionsListener {

    @Inject
    lateinit var preference: Preference
    private val gps: LocationServices by lazy { LocationServices(this) }
    private var currentLocation: Suggestion? = null

    override fun getLayoutId() = R.layout.activity_start_initialize

    override fun getViewModelClass() = MainViewModel::class
    private val bottomSheetFindRegion = BottomSheetFindRegion {
        binding.tvCity.text = it.name
        currentLocation = it
        preference.city = it.name
        preference.locationId = it.id
    }

    override fun initView() {
        setSupportActionBar(binding.layoutAppbar.toolbar)
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(false)
            it.setDisplayShowTitleEnabled(false)
        }
        checkPermission()

        binding.tvCity.setOnClickListener {
            bottomSheetFindRegion.show(supportFragmentManager, "find_region")
        }

        binding.btnOk.setOnClickListener {
            if (currentLocation != null)
                viewModel.fetchPrayers(currentLocation!!.id)
        }
    }

    override fun initData(savedInstanceState: Bundle?) {
        viewModel.responseRegion.observe(this) {
            if (it !=null) {
                currentLocation = it
                binding.tvCity.text = it.name
            }
            else binding.tvCity.setText(R.string.location_not_found)
        }
        viewModel.responseFetchPrayers.observe(this) {
            when(it.status) {
                Status.SUCCESS -> {
                    binding.btnOk.let { btn ->
                        btn.isEnabled = true
                        btn.hideProgress(R.string.save)
                    }
                    if (it.data !=null && it.data.isNotEmpty()) {
                        preference.isInitialize = true
                        preference.notifications = arrayListOf("imsak","fajr","sunrise","dhuha","dhuhr","asr","maghrib","isha")
                        ReminderReceiver.enableReminder(this)
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else
                        createToast(getString(R.string.title_error)).show()
                }
                Status.ERROR -> {
                    createToast(getString(R.string.title_error)).show()
                    binding.btnOk.let { btn ->
                        btn.isEnabled = true
                        btn.hideProgress(R.string.save)
                    }
                }
                Status.LOADING -> {
                    binding.btnOk.showProgress {
                        this.progressColor = Color.WHITE
                        gravity = DrawableButton.GRAVITY_CENTER
                        binding.btnOk.isEnabled = false
                    }
                }
            }
        }
    }

    private fun checkPermission() {
        Dexter.withContext(this).withPermissions(arrayListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
            .withListener(this)
            .onSameThread()
            .check()
    }

    private fun loadAddress() {
        GlobalScope.launch(Dispatchers.Main) {
            delay(100)
            if (gps.location == null) {
                if (gps.retry()) {
                    delay(1000)
                    loadAddress()
                }
            }
            try {
                @Suppress("BlockingMethodInNonBlockingContext")
                val address = LocationServices.getLocationAddress(this@StartInitializeActivity, gps.location!!.latitude, gps.location!!.longitude)
                address?.let {
                    viewModel.findRegion(it.subAdminArea)
                    gps.stopUsingGPS()
                }
                GlobalScope.launch(Dispatchers.Main) {
                    if (address == null) {
                        binding.tvCity.setText(R.string.location_not_found)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9000) checkPermission()

    }

    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
        if (report!!.areAllPermissionsGranted()) {
            gps.callServices()
            loadAddress()
        } else if (report.isAnyPermissionPermanentlyDenied) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri: Uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivityForResult(intent, 9000)
        }
    }

    override fun onPermissionRationaleShouldBeShown(p0: MutableList<PermissionRequest>?, token: PermissionToken?) {
        token!!.continuePermissionRequest()
    }

    override fun onStop() {
        super.onStop()
        gps.stopUsingGPS()
    }
}