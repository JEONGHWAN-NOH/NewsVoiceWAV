package com.newsvoice.tts

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.share.ShareClient
import com.kakao.sdk.template.model.Button
import com.kakao.sdk.template.model.Content
import com.kakao.sdk.template.model.FeedTemplate
import com.kakao.sdk.template.model.Link
import java.io.File
import java.security.MessageDigest

/** Official Kakao Share SDK, without a private server or embedded account secret. */
object KakaoCards {
    @JvmStatic fun keyHash(activity: Activity): String = try {
        val info = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val sig = info.signingInfo!!.apkContentsSigners.first()
        Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()), Base64.NO_WRAP)
    } catch (e: Exception) { "확인 실패" }

    private fun template(image: String, drive: String, youtube: String): FeedTemplate {
        fun link(url: String) = Link(webUrl = url, mobileWebUrl = url)
        return FeedTemplate(
            content = Content(title = "오늘의 종합뉴스", description = "카카오톡 경량 영상 또는 YouTube 스트리밍을 선택하세요.",
                imageUrl = image, link = link(drive)),
            buttons = listOf(Button("카카오톡 보기", link(drive)), Button("YouTube 보기", link(youtube)))
        )
    }

    @JvmStatic fun share(activity: Activity, key: String, cover: String, drive: String, youtube: String, status: android.widget.TextView) {
        fun show(message: String) = activity.runOnUiThread { status.text = message; Toast.makeText(activity, message, Toast.LENGTH_LONG).show() }
        if (!key.matches(Regex("[a-fA-F0-9]{32}"))) { show("카카오 네이티브 앱 키를 등록하세요."); return }
        if (ShareLinks.drive(drive).isEmpty() || ShareLinks.youtube(youtube).isEmpty()) { show("Google Drive와 YouTube 영상 주소를 모두 등록하세요."); return }
        if (cover.isEmpty()) { show("먼저 영상 일괄 제작을 완료하세요."); return }
        try { KakaoSdk.init(activity.applicationContext, key) }
        catch (e: Exception) { show("카카오 SDK 초기화 실패: ${e.javaClass.simpleName}"); return }
        if (!ShareClient.instance.isKakaoTalkSharingAvailable(activity)) { show("카카오톡 앱을 설치하거나 업데이트하세요."); return }
        Thread {
            var temp: File? = null
            try {
                val bitmap = activity.contentResolver.openInputStream(Uri.parse(cover)).use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalStateException("표지 읽기 실패")
                // The existing artwork has painted sample buttons. Crop those away;
                // only actual SDK buttons will appear underneath the approved heading.
                val crop = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height * 48 / 100)
                val upload = File.createTempFile("kakao-card-", ".jpg", activity.cacheDir)
                temp = upload
                upload.outputStream().use { check(crop.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                crop.recycle(); bitmap.recycle()
                activity.runOnUiThread {
                    status.text = "표지 업로드 중…"
                    ShareClient.instance.uploadImage(upload) { result, error ->
                        upload.delete()
                        if (error != null || result == null) { show("카카오 표지 업로드 실패 · 앱 키와 키 해시를 확인하세요."); return@uploadImage }
                        val feed = template(result.infos.original.url, drive, youtube)
                        ShareClient.instance.shareDefault(activity, feed) { sharing, shareError ->
                            if (shareError != null || sharing == null) { show("카카오 카드 생성 실패 · 앱 키, 등록 도메인 및 카카오톡 설정을 확인하세요."); return@shareDefault }
                            if (!sharing.warningMsg.isNullOrEmpty() || !sharing.argumentMsg.isNullOrEmpty()) {
                                show("카카오 공유 경고: 등록 도메인을 확인하세요. 카드 전송을 중단합니다."); return@shareDefault
                            }
                            activity.runOnUiThread { try { activity.startActivity(sharing.intent); status.text = "카카오톡에서 수신자를 선택하고 전송하세요." }
                            catch(e: Exception) { show("카카오톡 실행 실패: ${e.javaClass.simpleName}") } }
                        }
                    }
                }
            } catch (e: Exception) { temp?.delete(); show("카카오 카드 제작 실패: ${e.javaClass.simpleName}") }
        }.start()
    }
}
