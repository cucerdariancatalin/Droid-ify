package com.looker.droidify.utility

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import com.looker.core_common.hex
import com.looker.core_common.sdkAbove
import com.looker.core_model.InstalledItem
import com.looker.core_model.Product
import com.looker.core_model.Repository
import com.looker.droidify.R
import com.looker.droidify.service.Connection
import com.looker.droidify.service.DownloadService
import com.looker.droidify.utility.extension.android.Android
import com.looker.droidify.utility.extension.android.singleSignature
import com.looker.droidify.utility.extension.android.versionCodeCompat
import com.looker.droidify.utility.extension.resources.getColorFromAttr
import com.looker.droidify.utility.extension.resources.getDrawableCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.util.*

object Utils {
	private fun createDefaultApplicationIcon(context: Context, tintAttrResId: Int): Drawable {
		return context.getDrawableCompat(R.drawable.ic_application_default).mutate()
			.apply { setTintList(context.getColorFromAttr(tintAttrResId)) }
	}

	fun PackageInfo.toInstalledItem(): InstalledItem {
		val signatureString = singleSignature?.calculateHash.orEmpty()
		return InstalledItem(
			packageName,
			versionName.orEmpty(),
			versionCodeCompat,
			signatureString
		)
	}

	fun getDefaultApplicationIcons(context: Context): Pair<Drawable, Drawable> {
		val progressIcon: Drawable =
			createDefaultApplicationIcon(context, android.R.attr.textColorSecondary)
		val defaultIcon: Drawable =
			createDefaultApplicationIcon(context, R.attr.colorAccent)
		return Pair(progressIcon, defaultIcon)
	}

	fun getToolbarIcon(context: Context, resId: Int): Drawable {
		return context.getDrawableCompat(resId).mutate()
	}

	inline val Signature.calculateHash
		get() = MessageDigest.getInstance("MD5")
			.digest(toCharsString().toByteArray())
			.hex()

	inline val Certificate.fingerprint: String
		get() {
			val encoded = try {
				encoded
			} catch (e: CertificateEncodingException) {
				null
			}
			return encoded?.fingerprint.orEmpty()
		}

	inline val ByteArray.fingerprint: String
		get() {
			return if (size >= 256) {
				try {
					val fingerprint = MessageDigest.getInstance("SHA-256").digest(this)
					val builder = StringBuilder()
					for (byte in fingerprint) {
						builder.append("%02X".format(Locale.US, byte.toInt() and 0xff))
					}
					builder.toString()
				} catch (e: Exception) {
					e.printStackTrace()
					""
				}
			} else {
				""
			}
		}

	suspend fun startUpdate(
		packageName: String,
		installedItem: InstalledItem?,
		products: List<Pair<Product, Repository>>,
		downloadConnection: Connection<DownloadService.Binder, DownloadService>,
	) {
		val productRepository = Product.findSuggested(products, installedItem) { it.first }
		val compatibleReleases = productRepository?.first?.selectedReleases.orEmpty()
			.filter { installedItem == null || installedItem.signature == it.signature }
		val releaseFlow = MutableStateFlow(compatibleReleases.firstOrNull())
		if (compatibleReleases.size > 1) {
			releaseFlow.emit(
				compatibleReleases
					.filter { it.platforms.contains(Android.primaryPlatform) }
					.minByOrNull { it.platforms.size }
					?: compatibleReleases.minByOrNull { it.platforms.size }
					?: compatibleReleases.firstOrNull()
			)
		}
		val binder = downloadConnection.binder
		releaseFlow.collect {
			if (productRepository != null && it != null && binder != null) {
				binder.enqueue(
					packageName,
					productRepository.first.name,
					productRepository.second,
					it
				)
			}
		}
	}

	fun Context.setLanguage(language: String): Configuration {
		val config = resources.configuration
		if (language != "system") {
			val newLocale = getLocaleOfCode(language)
			Locale.setDefault(newLocale)
			config.setLocale(newLocale)
		} else {
			val locale = Locale.getDefault()
			config.setLocale(locale)
		}
		return config
	}

	private fun Context.getLocaleOfCode(localeCode: String): Locale = when {
		localeCode.isEmpty() -> sdkAbove(
			sdk = Build.VERSION_CODES.N,
			onSuccessful = { resources.configuration.locales[0] },
			orElse = { resources.configuration.locale }
		)
		localeCode.contains("-r") -> Locale(
			localeCode.substring(0, 2),
			localeCode.substring(4)
		)
		localeCode.contains("_") -> Locale(
			localeCode.substring(0, 2),
			localeCode.substring(3)
		)
		else -> Locale(localeCode)
	}
}
