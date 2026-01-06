package com.solusibejo.flutter_dynamic_icon_plus

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result


/** FlutterDynamicIconPlusPlugin */
class FlutterDynamicIconPlusPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel : MethodChannel
    private var activity: Activity? = null

    companion object {
        const val pluginName = "flutter_dynamic_icon_plus"
        const val appIcon = "app_icon"
        private const val TAG = "DynamicIconPlugin"
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, pluginName)
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when(call.method){
            MethodNames.setAlternateIconName -> {
                if(activity != null){
                    val sp = activity?.getSharedPreferences(pluginName, Context.MODE_PRIVATE)
                    val iconName = call.argument<String?>(Arguments.iconName)
                    val brandsInString = call.argument<String?>(Arguments.brands)
                    val manufacturesInString = call.argument<String?>(Arguments.manufactures)
                    val modelsInString = call.argument<String?>(Arguments.models)

                    Log.d(TAG, "setAlternateIconName called with iconName: $iconName")
                    Log.d(TAG, "Blacklist - brands: $brandsInString, manufactures: $manufacturesInString, models: $modelsInString")

                    // Lưu iconName vào SharedPreferences
                    val saved = sp?.edit()?.putString(appIcon, iconName)?.commit()
                    Log.d(TAG, "Saved app icon to SharedPreferences: $saved")

                    if(saved == true){
                        if(!containsOnBlacklist(brandsInString, manufacturesInString, modelsInString)){
                            // Device KHÔNG nằm trong blacklist -> đổi icon ngay lập tức
                            if(activity != null && iconName != null && iconName.isNotEmpty()){
                                Log.d(TAG, "Device not in blacklist, changing icon immediately")

                                ComponentUtil.changeAppIcon(
                                    activity!!,
                                    activity!!.packageManager,
                                    activity!!.packageName
                                )

                                // CHỈ xóa preference SAU KHI đã đổi icon thành công
                                // Việc này để tránh đổi lại icon khi app restart
                                ComponentUtil.removeCurrentAppIcon(activity!!)
                            } else if (iconName.isNullOrEmpty()) {
                                // Nếu iconName rỗng hoặc null -> reset về icon mặc định
                                Log.d(TAG, "iconName is null/empty, resetting to default icon")
                                ComponentUtil.resetToDefaultIcon(
                                    activity!!,
                                    activity!!.packageManager,
                                    activity!!.packageName
                                )
                            }
                        }
                        else {
                            // Device NẰM trong blacklist -> dùng Service để đổi icon khi app đóng
                            Log.d(TAG, "Device in blacklist, starting service for deferred icon change")
                            val flutterDynamicIconPlusService = Intent(activity, FlutterDynamicIconPlusService::class.java)
                            activity?.startService(flutterDynamicIconPlusService)
                            // KHÔNG gọi removeCurrentAppIcon() ở đây vì Service cần đọc giá trị này
                        }

                        result.success(true)
                    }
                    else {
                        Log.e(TAG, "Failed to save iconName to SharedPreferences")
                        result.error(
                            "500",
                            "Failed store $iconName to local storage",
                            "When failed store to local storage we will provide wrong value on method getAlternateIconName"
                        )
                    }
                }
                else {
                    Log.e(TAG, "Activity is null")
                    result.error("500", "Activity not found", "Activity didn't attached")
                }
            }

            MethodNames.supportsAlternateIcons -> {
                if(activity != null){
                    val packageInfo = ComponentUtil.packageInfo(activity!!)
                    // By default, we have one activity (MainActivity).
                    // If there is more than one activity, it indicates that we have an alternative activity
                    val isAlternateAvailable = packageInfo.activities?.size ?: 0 > 1
                    Log.d(TAG, "supportsAlternateIcons: $isAlternateAvailable (activities count: ${packageInfo.activities?.size})")
                    result.success(isAlternateAvailable)
                }
                else {
                    result.success(false)
                }
            }

            MethodNames.getAlternateIconName -> {
                if(activity != null){
                    val enabledComponent = ComponentUtil.getCurrentEnabledAlias(activity!!)
                    Log.d(TAG, "getAlternateIconName: ${enabledComponent?.name}")
                    result.success(enabledComponent?.name)
                }
                else {
                    result.error("500", "Activity not found", "Activity didn't attached")
                }
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    private fun containsOnBlacklist(brandsInString: String?, manufacturesInString: String?, modelsInString: String?): Boolean {
        val brands = brandsInString?.split(",")?.map { it.trim() }
        val manufactures = manufacturesInString?.split(',')?.map { it.trim() }
        val models = modelsInString?.split(',')?.map { it.trim() }

        val deviceBrand = Build.BRAND
        val deviceManufacture = Build.MANUFACTURER
        val deviceModel = Build.MODEL

        Log.d(TAG, "Device info - Brand: $deviceBrand, Manufacturer: $deviceManufacture, Model: $deviceModel")

        if (brands != null) {
            for(brand in brands){
                if(brand.isNotEmpty() && deviceBrand.equals(brand, ignoreCase = true)){
                    Log.d(TAG, "Device brand '$deviceBrand' matches blacklist")
                    return true
                }
            }
        }

        if(manufactures != null){
            for(manufacture in manufactures){
                if(manufacture.isNotEmpty() && deviceManufacture.equals(manufacture, ignoreCase = true)){
                    Log.d(TAG, "Device manufacturer '$deviceManufacture' matches blacklist")
                    return true
                }
            }
        }

        if(models != null){
            for(model in models){
                if(model.isNotEmpty() && deviceModel.equals(model, ignoreCase = true)){
                    Log.d(TAG, "Device model '$deviceModel' matches blacklist")
                    return true
                }
            }
        }

        return false
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        Log.d(TAG, "Activity attached: ${activity?.javaClass?.simpleName}")
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }
}