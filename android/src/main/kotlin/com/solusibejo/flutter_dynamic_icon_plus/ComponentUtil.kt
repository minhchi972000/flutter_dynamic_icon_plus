package com.solusibejo.flutter_dynamic_icon_plus

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log


object ComponentUtil {
    private const val TAG = "ComponentUtil"

    private fun enable(
        context: Context,
        packageManager: PackageManager,
        componentNameString: String,
    ) {
        try {
            val componentName = ComponentName(context, componentNameString)
            Log.d(TAG, "Enabling component: $componentNameString")

            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "Successfully enabled: $componentNameString")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling component: $componentNameString", e)
        }
    }

    private fun disable(
        context: Context,
        packageManager: PackageManager,
        componentNameString: String,
    ) {
        try {
            val componentName = ComponentName(context, componentNameString)
            Log.d(TAG, "Disabling component: $componentNameString")

            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "Successfully disabled: $componentNameString")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling component: $componentNameString", e)
        }
    }

    fun packageInfo(context: Context): PackageInfo {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val component = PackageManager.GET_ACTIVITIES or PackageManager.GET_DISABLED_COMPONENTS

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName, PackageManager.PackageInfoFlags.of(
                    component.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION") packageManager.getPackageInfo(
                packageName,
                component
            )
        }
    }

    fun getCurrentEnabledAlias(context: Context): ActivityInfo? {
        val packageManager = context.packageManager
        val packageName = context.packageName
        return try {
            val info = packageInfo(context)
            var enabled: ActivityInfo? = null
            val activities = info.activities
            if (activities != null) {
                for (activityInfo in activities) {
                    // Only checks among the `activity-alias`s, for current enabled alias
                    if (activityInfo.targetActivity != null) {
                        val isEnabled: Boolean =
                            isComponentEnabled(context, packageManager, packageName, activityInfo.name)
                        if (isEnabled) {
                            enabled = activityInfo
                            Log.d(TAG, "Found enabled alias: ${activityInfo.name}")
                        }
                    }
                }
            }
            enabled
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package not found", e)
            null
        }
    }

    private fun isComponentEnabled(
        context: Context,
        pm: PackageManager,
        pkgName: String?,
        clsName: String
    ): Boolean {
        val componentName = ComponentName(pkgName!!, clsName)
        return when (pm.getComponentEnabledSetting(componentName)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> {
                try {
                    val packageInfo = packageInfo(context)
                    val components = ArrayList<ComponentInfo>()
                    packageInfo.activities?.let { components.addAll(it) }

                    for (componentInfo in components) {
                        if (componentInfo.name == clsName) {
                            return componentInfo.isEnabled
                        }
                    }
                    false
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }
            else -> {
                try {
                    val packageInfo = packageInfo(context)
                    val components = ArrayList<ComponentInfo>()
                    packageInfo.activities?.let { components.addAll(it) }
                    for (componentInfo in components) {
                        if (componentInfo.name == clsName) {
                            return componentInfo.isEnabled
                        }
                    }
                    false
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }
        }
    }

    private fun getAllActivityAliases(context: Context): List<ActivityInfo> {
        val aliases = ArrayList<ActivityInfo>()
        try {
            val info = packageInfo(context)
            info.activities?.forEach { activityInfo ->
                if (activityInfo.targetActivity != null) {
                    aliases.add(activityInfo)
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package not found", e)
        }
        return aliases
    }

    private fun getMainActivity(context: Context): ActivityInfo? {
        try {
            val info = packageInfo(context)
            info.activities?.forEach { activityInfo ->
                if (activityInfo.targetActivity == null) {
                    return activityInfo
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package not found", e)
        }
        return null
    }

    fun changeAppIcon(context: Context, packageManager: PackageManager, packageName: String) {
        val sp = context.getSharedPreferences(FlutterDynamicIconPlusPlugin.pluginName, Context.MODE_PRIVATE)
        val targetIconName = sp.getString(FlutterDynamicIconPlusPlugin.appIcon, null)

        Log.d(TAG, "changeAppIcon called, target icon: $targetIconName")

        if (targetIconName.isNullOrEmpty()) {
            Log.d(TAG, "No target icon specified")
            return
        }

        val currentlyEnabled = getCurrentEnabledAlias(context)
        Log.d(TAG, "Currently enabled alias: ${currentlyEnabled?.name}")

        // Build full component name if needed
        val fullTargetName = if (targetIconName.startsWith(packageName)) {
            targetIconName
        } else if (targetIconName.startsWith(".")) {
            "$packageName$targetIconName"
        } else {
            "$packageName.$targetIconName"
        }

        Log.d(TAG, "Full target name: $fullTargetName")

        // Check if already enabled
        if (currentlyEnabled?.name == fullTargetName) {
            Log.d(TAG, "Target icon already enabled, skipping")
            return
        }

        setupIcon(context, packageManager, packageName, fullTargetName, currentlyEnabled?.name)
    }

    fun removeCurrentAppIcon(context: Context) {
        val sp = context.getSharedPreferences(FlutterDynamicIconPlusPlugin.pluginName, Context.MODE_PRIVATE)
        sp.edit().remove(FlutterDynamicIconPlusPlugin.appIcon).apply()
        Log.d(TAG, "Removed stored app icon preference")
    }

    private fun setupIcon(
        context: Context,
        packageManager: PackageManager,
        packageName: String,
        newIconName: String?,
        currentlyEnabledName: String?
    ) {
        Log.d(TAG, "setupIcon: new=$newIconName, current=$currentlyEnabledName")

        if (newIconName.isNullOrEmpty()) {
            Log.d(TAG, "New icon name is null or empty")
            return
        }

        // Get MainActivity info
        val mainActivity = getMainActivity(context)
        val mainActivityName = mainActivity?.name

        Log.d(TAG, "MainActivity: $mainActivityName")

        // Get all aliases
        val allAliases = getAllActivityAliases(context)
        Log.d(TAG, "All aliases: ${allAliases.map { it.name }}")

        // Find the target alias
        val targetAlias = allAliases.find { it.name == newIconName }

        if (targetAlias == null) {
            Log.e(TAG, "Target alias not found: $newIconName")
            Log.e(TAG, "Available aliases: ${allAliases.map { it.name }}")
            return
        }

        // Step 1: Enable the new alias
        enable(context, packageManager, newIconName)

        // Step 2: Disable all other aliases
        allAliases.forEach { alias ->
            if (alias.name != newIconName) {
                disable(context, packageManager, alias.name)
            }
        }

        // Step 3: Disable MainActivity if it exists and is not the target
        if (mainActivityName != null && mainActivityName != newIconName) {
            disable(context, packageManager, mainActivityName)
        }

        Log.d(TAG, "Icon change completed: $newIconName")
    }

    /**
     * Reset to default MainActivity icon
     */
    fun resetToDefaultIcon(context: Context, packageManager: PackageManager, packageName: String) {
        Log.d(TAG, "Resetting to default icon")

        val mainActivity = getMainActivity(context)
        val allAliases = getAllActivityAliases(context)

        // Enable MainActivity
        mainActivity?.let {
            enable(context, packageManager, it.name)
        }

        // Disable all aliases
        allAliases.forEach { alias ->
            disable(context, packageManager, alias.name)
        }

        removeCurrentAppIcon(context)
        Log.d(TAG, "Reset to default icon completed")
    }
}