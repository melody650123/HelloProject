# MockDemo 修改紀錄

本次更新以「讓初診 MockDemo 可以完整 Demo」為目標，涵蓋聊天頁、醫師頁、確認頁、預錄語音與複診語音辨識。相對於 `0ae622d`（initial import），**後端程式碼沒有修改**，改動都在 Android 前端與設定。

驗證狀態：Android 單元測試 130 個全數通過，`assembleDebug` 建置成功，已安裝於實機 Samsung Galaxy A55 測試。

---

## 一、Demo 流程概覽

```
VisitTypeSelection → 初診 → Chat（固定劇本 + 預錄語音）
  → 看推薦醫師 → POST /mock-demo/prepare → DoctorSelection（真實 DB 班表 + 預錄朗讀）
  → 選醫師 → POST /generate_script → ConfirmNeed（預約資訊確認）
```

| 項目 | 是否固定 | 說明 |
|---|---|---|
| 聊天台詞（右膝疼痛 → 一般骨科） | 固定 | `demo/MockDemoScript.kt` |
| 科別、初診、偏好上午 | 固定 | 後端 `/mock-demo/prepare` |
| 推薦醫師、日期、時段 | **即時查 DB** | 以「今天起 21 天」查詢，詳見〈五、注意事項〉 |

---

## 二、前端修改

### 1. 聊天頁（`ChatScreen.kt`、`MockDemoScript.kt`）
- **最後一句加粗科別**：Demo 結尾「依照你目前提供的資訊，建議先掛**一般骨科**。…」中的科別以粗體顯示。
  - 科別名稱抽成 `MockDemoScript.department`。
  - 加粗邏輯抽成可測試的 `boldRanges()`，同時支援原有的「檢傷結果」。
- **語言選擇改為共用設定**：聊天頁的國語／台語切換改讀寫 `VoiceLanguagePreference`，進入醫師頁後會沿用同一語言。此設定會保存在手機上，重開 App 仍保留。

### 2. 醫師選擇頁（`DoctorSelectionScreen.kt`）
- **移除卡片日期下方的推薦理由小字**。推薦理由仍可在「詳情資訊」中查看。
- **剛進入時每位醫師只顯示一次**：
  - 未選日期時，每位醫師只顯示排名最前的時段。
  - 點選日期氣泡後，顯示當天所有時段。
  - 邏輯在 `displayedDoctorRecommendations()`。
- **醫師卡片朗讀優先播放預錄檔**：`DoctorCardSpeech` 先查預錄音檔，找不到才呼叫後端 `/voice/tts`。

### 3. 預約資訊確認頁（`ConfirmNeedScreen.kt`）
- **預約時間不再從字中間斷行**：「2026/10/06 (二) 上午診」三段內部插入 U+2060（Word Joiner），只允許在段與段之間換行，避免在 Samsung 手機上出現「上午」／「診」被拆開。

### 4. 複診語音辨識（`ChatViewModel.kt`、`ChatScreen.kt`）
- **複診的國語、台語都改走後端 `/voice/asr`**（語音服務 Voice Gateway）。
- **初診 Demo 維持不變**：國語用手機系統辨識，台語不開放錄音。
- `stopTaiwaneseRecordingAndTranscribe()` 改為 `stopRecordingAndTranscribe(lang)`。新增 `usesBackendAsr()`，規則是只要不是初診 Demo 劇本，就走後端辨識。

### 5. 其他一併上傳的改動（非本次對話中修改）
- 醫師卡片自然句朗讀（`spokenIntroduction()`），例如「推薦您蘇宇平醫師。科別為一般骨科。時間為10月6日星期二上午八點半到下午十二點。」
  - 只有排名第一的卡片會以「推薦您」開頭。
  - 不朗讀診間、推薦理由與專長標籤。
- 醫師頁頁首顯示目前語音語言（「語音：國語／台語」）。
- `repository/VoiceLanguagePreference.kt`：跨頁共用並保存語音語言設定。

---

## 三、預錄語音

所有預錄檔放在 `android/app/src/main/res/raw/`，對應關係集中在 `util/FixedTriageAudioResolver.kt`。對應方式是以「朗讀文字完全相符」來比對，所以**台詞或朗讀句型一旦變更，就必須重錄**。

| 用途 | 國語 | 台語 | 來源 |
|---|---|---|---|
| 聊天 Demo 8 句（開場白 + 7 句回覆） | `mockdemo_chat_01~08_zh.m4a` | `mockdemo_chat_01~08_taigi.wav` | `MockVoice/zh`、`MockVoice/taigi_M` |
| 醫師卡片 5 句 | `mockdemo_doctor_01~05_zh.m4a` | `mockdemo_doctor_01~05_taigi.m4a` | `MockVoice/zh`、`MockVoice/taigi` |

- 原始音檔與醫師朗讀稿（`MockVoice/mockdemo_doctor_script.md`）只保留在本機的 `MockVoice/`，不納入版控（已加入 `.gitignore`）。App 使用的音檔已複製到 `res/raw/`。
- 醫師朗讀文字存放在 `MockDemoScript.doctorIntroductions`，內容依 2026-10-03 的 `/recommend` 結果產生。
- 有預錄檔的句子不需要網路或語音服務就能播放。

---

## 四、測試

| 測試檔 | 內容 |
|---|---|
| `MockDemoScriptUnitTest` | 劇本文字、科別加粗範圍、聊天預錄檔國台語對應 |
| `RecommendationIntegrationUnitTest` | 醫師頁去除重複醫師與日期篩選 |
| `ConfirmVisitTimeFormatUnitTest`（新增） | 預約時間只在日期、星期、時段之間換行 |
| `DoctorCardSpeechUnitTest`（新增） | 朗讀句與預錄文字一字不差；有預錄檔時不呼叫後端 TTS |
| `VoiceFlowUnitTest` | 只有真實問診會走後端 ASR |

```powershell
cd android
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat assembleDebug --no-daemon
```

---

## 五、注意事項

### 推薦結果會隨日期變動
- 推薦以「今天起 21 天」查詢 DB。模擬 10/03 至 10/05 的結果都相同：蘇宇平 10/6、10/13、10/16、10/20，邱方遙 10/16，皆為上午。
- **10/6 上午門診結束後**，第一筆會消失、順序往前遞補，醫師預錄朗讀就對不上。對不上的卡片會改用後端 TTS。
- 12:00 目前朗讀為「下午十二點」，預錄檔也是照這個版本錄的。

### 語音服務（Voice Gateway）
- 目前沒有連上（`/voice/health` 回 `gateway: unreachable`）。
- 受影響的功能：複診的語音辨識（國語、台語），以及沒有預錄檔的朗讀。
- 接上方式：在語音服務那台電腦以 `0.0.0.0:8000` 啟動，並把 `backend/.env` 的 `VOICE_GATEWAY_URL` 改為該位址，例如 `http://100.64.116.35:8000`。

### 本機環境（不進版控）
- `backend/.env`：`DB_SERVER` 指向 310 電腦，例如 `100.64.116.35,1433`，經 Tailscale 連線。
- `android/local.properties`：`API_BASE_URL` 填後端電腦在手機所連網路上的 IP，例如 `http://10.147.24.64:8080`。**改 IP 後要重新建置 APK。**
- 後端啟動：`.\scripts\run-backend.ps1 -Port 8080 -HostName 0.0.0.0`

### 已知未處理
- 確認頁「重新詢問」會導向 `Route.CHAT`（未指定就診類型），會離開 MockDemo 劇本並改走一般問診，Demo 時請勿點選。
- 後端有 5 個測試失敗，都與 MockDemo 無關：
  - 1 個因星期幾而結果不同的日期測試。
  - 4 個依賴 `.env` 中 `BATCH_TRIAGE_ENABLED=true` 的 strict-keyed 測試。
