import SwiftUI

/// لون الحالة: الطبيعي بلون التطبيق، القريب من الحد برتقالي، والبعيد أحمر
func statusColor(_ status: VitalStatus) -> Color {
    switch status {
    case .normal: return Brand.mint
    case .elevated, .low: return Brand.warning
    default: return Brand.danger
    }
}

private func vitalIcon(_ type: VitalType) -> String {
    switch type {
    case .pulse: return "waveform.path.ecg"
    case .pressure: return "heart.fill"
    case .sugar: return "drop.fill"
    case .temperature: return "thermometer"
    }
}

private func vitalTint(_ type: VitalType) -> Color {
    switch type {
    case .pulse: return Color(hex: 0xFF6B8A)
    case .pressure: return Color(hex: 0xE5484D)
    case .sugar: return Color(hex: 0xB28CFF)
    case .temperature: return Color(hex: 0xFFA62B)
    }
}

/// آخر قراءة لكل علامة، كما تُعرض في البطاقة
private struct Latest {
    let value: String
    let status: VitalStatus
    let time: String
    let note: String?
}

/// الرابط الصغير يسار التحية
struct VitalsLink: View {
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            VStack(spacing: 0) {
                Image(systemName: "waveform.path.ecg").font(.system(size: 22, weight: .semibold)).foregroundColor(Brand.glow)
                Text(tr("العلامات", "Vital")).style(12, .medium, Brand.secondaryText)
                Text(tr("الحيوية", "signs")).style(12, .medium, Brand.secondaryText)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .glass(radius: 18)
        }
        .buttonStyle(PressStyle())
        .accessibilityLabel(tr("العلامات الحيوية", "Vital signs"))
    }
}

/// العلامات الحيوية: النبض بالكاميرا، والضغط والسكر والحرارة إدخال يدوي
struct VitalsScreen: View {
    let onClose: () -> Void
    let onPulse: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var entry: VitalType?

    private func latest() -> [VitalType: Latest] {
        guard let patientId = model.patient?.id else { return [:] }
        var map: [VitalType: Latest] = [:]
        if let reading = HeartRateRepository.recent(patientId, limit: 1).first {
            map[.pulse] = Latest(value: "\(reading.bpm)", status: VitalRanges.pulse(reading.bpm), time: Format.readingTime(reading.measuredAt), note: tr("بالكاميرا", "camera"))
        }
        for type in [VitalType.pressure, .sugar, .temperature] {
            if let reading = VitalsRepository.recent(patientId, type, limit: 1).first {
                map[type] = Latest(value: VitalRanges.display(reading), status: VitalRanges.of(reading), time: Format.readingTime(reading.measuredAt),
                                   note: type == .sugar ? SugarContext.of(reading.context).title : nil)
            }
        }
        return map
    }

    var body: some View {
        let values = latest()
        FullScreen {
            ScreenHeader(title: tr("العلامات الحيوية", "Vital signs"), onClose: onClose)
            ScrollView {
                VStack(spacing: 12) {
                    Text(tr("اضغط على أي علامة لتسجّل قراءة جديدة أو ترى قراءاتك السابقة.", "Tap any sign to add a new reading or see your previous readings."))
                        .style(15, .regular, Brand.secondaryText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 6)
                    ForEach([[VitalType.pulse, .pressure], [.sugar, .temperature]], id: \.self) { row in
                        HStack(alignment: .top, spacing: 12) {
                            ForEach(row) { type in
                                VitalCard(type: type, latest: values[type]) { if type == .pulse { onPulse() } else { entry = type } }
                            }
                        }
                        .fixedSize(horizontal: false, vertical: true)
                    }
                    Text(tr("للمتابعة فقط، وليست تشخيصًا طبيًا. القراءات محفوظة على جوالك.", "For tracking only, not a medical diagnosis. Readings are saved on your phone."))
                        .style(14, .regular, Brand.secondaryText).multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.top, 6)
                }
                .padding(.horizontal, 14)
                .padding(.top, 8)
                .padding(.bottom, 24)
                .id(model.vitalsVersion)
            }
        }
        .afiatiSheet(item: $entry) { type in
            VitalEntrySheet(type: type, onDismiss: { entry = nil })
        }
    }
}

private struct VitalCard: View {
    let type: VitalType
    let latest: Latest?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: vitalIcon(type)).font(.system(size: 20)).foregroundColor(vitalTint(type))
                        .frame(width: 40, height: 40).background(vitalTint(type).opacity(0.16), in: Circle())
                    Text(type.title).style(17, .bold).lineLimit(2).minimumScaleFactor(0.8)
                }
                if let latest = latest {
                    HStack(alignment: .lastTextBaseline, spacing: 4) {
                        Text(latest.value).style(30, .bold).lineLimit(1).minimumScaleFactor(0.7)
                        Text(type.unit).style(13, .regular, Brand.secondaryText)
                    }
                    StatusChip(status: latest.status)
                    Text([latest.note, latest.time].compactMap { $0 }.joined(separator: " · ")).style(13, .regular, Brand.secondaryText)
                } else {
                    Text("—").style(30, .bold, Brand.secondaryText)
                    Text(type == .pulse ? tr("اضغط للقياس بالكاميرا", "Tap to measure with the camera") : tr("اضغط لتسجيل قراءة", "Tap to add a reading")).style(14, .regular, Brand.glow)
                }
                Spacer(minLength: 0)
                Text(VitalRanges.normalText(type)).style(12, .regular, Brand.secondaryText)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(16)
            .glass(radius: 24)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .accessibilityLabel(type.title)
    }
}

struct StatusChip: View {
    let status: VitalStatus

    var body: some View {
        let color = statusColor(status)
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(status.title).style(13, .bold, color)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 3)
        .background(color.opacity(0.16), in: Capsule())
    }
}

/// تسجيل قراءة يدوية بخانات كبيرة، وتحتها آخر القراءات
private struct VitalEntrySheet: View {
    let type: VitalType
    let onDismiss: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var first = ""
    @State private var second = ""
    @State private var sugarContext = SugarContext.fasting
    @State private var error: String?
    @State private var deleting: VitalReading?

    private func number(_ text: String) -> Double? {
        // الأرقام العربية الهندية إلى لاتينية، والفاصلة العشرية العربية إلى نقطة
        let map: [Character: Character] = ["٠": "0", "١": "1", "٢": "2", "٣": "3", "٤": "4", "٥": "5", "٦": "6", "٧": "7", "٨": "8", "٩": "9", "٫": ".", ",": "."]
        let latin = String(text.trimmingCharacters(in: .whitespaces).map { map[$0] ?? $0 })
        return Double(latin)
    }

    private func save() {
        let a = number(first)
        let b = number(second)
        switch type {
        case .pressure:
            if a == nil || b == nil { error = tr("اكتب الرقمين بالأرقام فقط.", "Type both numbers using digits only.") }
            else if !VitalRanges.systolicRange.contains(a!) || !VitalRanges.diastolicRange.contains(b!) { error = tr("تأكد من الرقمين؛ يبدو أن فيهما خطأ كتابيًا.", "Check both numbers; there seems to be a typo.") }
            else if a! <= b! { error = tr("الرقم الأعلى (الانقباضي) يجب أن يكون أكبر من الأدنى.", "The top number (systolic) must be greater than the bottom one.") }
            else { error = nil }
        case .sugar:
            error = a.map { VitalRanges.sugarRange.contains($0) } == true ? nil : tr("اكتب قراءة السكر كما في الجهاز (بين 20 و600).", "Type the sugar reading as shown on the meter (between 20 and 600).")
        default:
            error = a.map { VitalRanges.temperatureRange.contains($0) } == true ? nil : tr("اكتب درجة الحرارة بالمئوية، مثل 36.8", "Type the temperature in Celsius, e.g. 36.8")
        }
        guard error == nil, let value = a, let patientId = model.patient?.id else { return }
        do {
            let saved = try VitalsRepository.save(patientId, type, value: value, value2: type == .pressure ? b : nil, context: type == .sugar ? sugarContext.rawValue : nil)
            model.vitalsVersion += 1
            model.toast = tr("حُفظت القراءة · \(VitalRanges.of(saved).title)", "Reading saved · \(VitalRanges.of(saved).title)")
            first = ""
            second = ""
        } catch {
            self.error = tr("تعذّر حفظ القراءة.", "Couldn't save the reading.")
        }
    }

    var body: some View {
        let history = model.patient.map { VitalsRepository.recent($0.id, type, limit: 5) } ?? []
        let keyboard: UIKeyboardType = type == .temperature ? .decimalPad : .numberPad
        AfiatiSheet(title: type.title, onDismiss: onDismiss) {
            VStack(alignment: .leading, spacing: 10) {
                switch type {
                case .pressure:
                    Text(tr("اكتب الرقمين كما يظهران في جهاز الضغط", "Type both numbers as shown on the blood pressure monitor")).style(16, .regular, Brand.secondaryText)
                    // من اليسار إلى اليمين كما يُكتب الضغط وكما يظهر في الجهاز: 120 / 80
                    HStack(alignment: .bottom, spacing: 10) {
                        VStack(spacing: 4) {
                            Text(tr("الانقباضي (الأعلى)", "Systolic (top)")).style(15, .regular, Brand.secondaryText)
                            GlassField(text: Binding(get: { first }, set: { first = $0; error = nil }), placeholder: "120", size: 26, weight: .bold, centered: true, keyboard: keyboard, ltr: true)
                        }
                        Text("/").style(30, .bold, Brand.secondaryText).padding(.bottom, 14)
                        VStack(spacing: 4) {
                            Text(tr("الانبساطي (الأدنى)", "Diastolic (bottom)")).style(15, .regular, Brand.secondaryText)
                            GlassField(text: Binding(get: { second }, set: { second = $0; error = nil }), placeholder: "80", size: 26, weight: .bold, centered: true, keyboard: keyboard, ltr: true)
                        }
                    }
                    .environment(\.layoutDirection, .leftToRight)
                case .sugar:
                    FieldLabel(tr("متى قست السكر؟", "When did you measure?"))
                    ChipRow(items: SugarContext.allCases, title: { $0.short }, selected: { sugarContext == $0 }, action: { sugarContext = $0 })
                    Text(sugarContext.hint).style(14, .regular, Brand.secondaryText)
                    FieldLabel(tr("القراءة (\(type.unit))", "Reading (\(type.unit))"))
                    GlassField(text: Binding(get: { first }, set: { first = $0; error = nil }), placeholder: "100", size: 26, weight: .bold, centered: true, keyboard: keyboard, ltr: true)
                default:
                    FieldLabel(tr("القراءة (\(type.unit))", "Reading (\(type.unit))"))
                    GlassField(text: Binding(get: { first }, set: { first = $0; error = nil }), placeholder: "36.8", size: 26, weight: .bold, centered: true, keyboard: keyboard, ltr: true)
                }
                Text(VitalRanges.normalText(type, sugarContext)).style(14, .regular, Brand.secondaryText)
                if let error = error { Text(error).style(16, .medium, Brand.danger) }
                PrimaryButton(tr("حفظ القراءة", "Save reading"), icon: "square.and.arrow.down",
                              enabled: !first.trimmingCharacters(in: .whitespaces).isEmpty && (type != .pressure || !second.trimmingCharacters(in: .whitespaces).isEmpty)) { save() }
                    .padding(.top, 4)

                if !history.isEmpty {
                    FieldLabel(tr("آخر القراءات", "Latest readings")).padding(.top, 10)
                    ForEach(history) { reading in
                        let status = VitalRanges.of(reading)
                        Button { deleting = reading } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 0) {
                                    HStack(alignment: .lastTextBaseline, spacing: 4) {
                                        Text(VitalRanges.display(reading)).style(22, .bold)
                                        Text(type.unit).style(12, .regular, Brand.secondaryText)
                                    }
                                    Text([type == .sugar ? SugarContext.of(reading.context).title : nil, Format.readingTime(reading.measuredAt)].compactMap { $0 }.joined(separator: " · "))
                                        .style(13, .regular, Brand.secondaryText)
                                }
                                Spacer()
                                StatusChip(status: status)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .glass(radius: 18)
                        }
                        .buttonStyle(PressStyle())
                        .accessibilityHint(tr("حذف القراءة", "Delete reading"))
                        if reading.id == history.first?.id, let advice = VitalRanges.advice(status) {
                            Text(advice).style(14, .regular, statusColor(status))
                        }
                    }
                    Text(tr("اضغط على قراءة لحذفها إن سُجّلت بالخطأ.", "Tap a reading to delete it if it was entered by mistake.")).style(13, .regular, Brand.secondaryText)
                }
            }
            .id(model.vitalsVersion)
        }
        .overlay {
            if let reading = deleting {
                AfiatiDialog(
                    title: tr("حذف القراءة؟", "Delete reading?"),
                    text: "\(type.title): \(VitalRanges.display(reading)) \(type.unit) · \(Format.readingTime(reading.measuredAt))",
                    confirm: tr("حذف", "Delete"), destructive: true, dismiss: tr("إلغاء", "Cancel"),
                    onConfirm: {
                        VitalsRepository.delete(reading.id)
                        model.vitalsVersion += 1
                        deleting = nil
                    },
                    onDismiss: { deleting = nil }
                )
            }
        }
    }
}
