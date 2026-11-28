import SwiftUI

struct MedicationsScreen: View {
    let header: AnyView
    let onAdd: () -> Void
    let onOpen: (Medication) -> Void
    @EnvironmentObject private var model: AppModel

    private var subtitle: String {
        let count = model.medications.count
        switch count {
        case 0: return tr("لا توجد أدوية بعد", "No medicines yet")
        case 1: return tr("دواء واحد نشط · اضغط عليه للتعديل", "One active medicine · tap it to edit")
        case 2: return tr("دواءان نشطان · اضغط على الدواء للتعديل", "Two active medicines · tap one to edit")
        case 3...10: return tr("\(count) أدوية نشطة · اضغط على الدواء للتعديل", "\(count) active medicines · tap one to edit")
        default: return tr("\(count) دواءً نشطًا · اضغط على الدواء للتعديل", "\(count) active medicines · tap one to edit")
        }
    }

    var body: some View {
        TabList(header: header) {
            BigTitle(title: tr("أدويتي", "My medicines"), subtitle: subtitle)
            if model.medications.isEmpty {
                EmptyCard(title: tr("قائمتك فارغة", "Your list is empty"),
                          message: tr("أضف أدويتك مرة واحدة، وعافيتي يتولى التذكير والمتابعة.", "Add your medicines once, and Afiati takes care of reminders and follow-up."),
                          action: tr("إضافة دواء", "Add medicine"), onAction: onAdd)
            } else {
                ForEach(model.medications) { medication in
                    Button { onOpen(medication) } label: { MedicationCard(medication: medication) }
                        .buttonStyle(PressStyle())
                        .contextMenu {
                            Button { model.present(RefillDialog(medication: medication)) } label: {
                                Label(tr("أضفت علبة جديدة", "I added a new box"), systemImage: "cart.badge.plus")
                            }
                            Button(role: .destructive) { model.present(StopDialog(medication: medication)) } label: {
                                Label(tr("إيقاف الدواء", "Stop medicine"), systemImage: "stop.circle")
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.bottom, 10)
                }
            }
        }
    }
}

struct MedicationCard: View {
    let medication: Medication

    var body: some View {
        HStack(spacing: 12) {
            IconBadge(icon: "pills.fill", color: Brand.avatarColor(medication.id), size: 52)
            VStack(alignment: .leading, spacing: 2) {
                Text(medication.name).style(20, .bold).lineLimit(1)
                Text("\(Format.dose(medication.doseAmount, medication.doseUnit)) · \(Format.schedule(medication.repeatType, medication.repeatDays, medication.times))")
                    .style(15, .regular, Brand.secondaryText).lineLimit(2)
                if let stock = medication.stockCount {
                    Text(medication.lowStock ? tr("المخزون: \(stock) · قارب على النفاد", "Stock: \(stock) · running low") : tr("المخزون: \(stock)", "Stock: \(stock)"))
                        .style(15, .medium, medication.lowStock ? Brand.warning : Brand.glow)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .glass(radius: 24)
        .contentShape(Rectangle())
    }
}

/// تفاصيل الدواء: الجدول والمخزون، وإضافة علبة، وإيقاف الدواء
struct MedicationSheet: View {
    let medication: Medication
    let onDismiss: () -> Void
    var onEdit: (Medication) -> Void = { _ in }
    @EnvironmentObject private var model: AppModel
    @State private var refilling = false
    @State private var stopping = false

    var body: some View {
        // أحدث نسخة من الدواء بعد أي تعديل
        let current = model.medications.first { $0.id == medication.id } ?? medication
        AfiatiSheet(title: nil, onDismiss: onDismiss) {
            VStack(spacing: 8) {
                IconBadge(icon: "pills.fill", color: Brand.avatarColor(current.id), size: 84)
                Text(current.name).style(30, .bold).multilineTextAlignment(.center)
                Text(Format.dose(current.doseAmount, current.doseUnit)).style(19, .regular, Brand.secondaryText)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 10)
            Card(radius: 24, spacing: 12) {
                InfoRow(label: tr("التكرار", "Repeats"),
                        value: "\(Format.repeatTitle(current.repeatType)) · \(Format.times(current.repeatType == RepeatType.daily ? current.times.count : current.repeatDays.count))")
                InfoRow(label: tr("المواعيد", "Times"), value: Format.schedule(current.repeatType, current.repeatDays, current.times))
                InfoRow(label: tr("المخزون", "Stock"), value: current.stockCount.map { current.lowStock ? tr("\($0) · قارب على النفاد", "\($0) · running low") : "\($0)" } ?? tr("لا يُتابع", "Not tracked"))
            }
            .padding(.top, 18)
            VStack(spacing: 10) {
                PrimaryButton(tr("أضفت علبة جديدة", "I added a new box"), icon: "cart.badge.plus") { refilling = true }
                GlassButton(tr("تعديل الدواء", "Edit medicine"), icon: "pencil") { onEdit(current) }
                GlassButton(tr("إيقاف هذا الدواء", "Stop this medicine"), icon: "stop.circle.fill", color: Brand.danger) { stopping = true }
            }
            .padding(.top, 16)
        }
        .overlay {
            if refilling {
                RefillDialog(medication: current, onClose: { refilling = false })
            } else if stopping {
                StopDialog(medication: current, onClose: { stopping = false }, onStopped: onDismiss)
            }
        }
    }
}

private struct InfoRow: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).style(15, .regular, Brand.secondaryText)
            Text(value).style(18, .medium)
        }
    }
}

/// كم أضاف إلى المخزون. onClose إن عُرضت داخل نافذة؛ وإلا تُغلق نافذة الشاشة الرئيسية.
struct RefillDialog: View {
    let medication: Medication
    var onClose: (() -> Void)?
    @EnvironmentObject private var model: AppModel
    @State private var amount = 30

    private func close() { if let onClose = onClose { onClose() } else { model.dismissDialog() } }

    var body: some View {
        AfiatiDialog(
            title: tr("كم أضفت إلى \(medication.name)؟", "How many did you add to \(medication.name)?"),
            confirm: tr("إضافة", "Add"), dismiss: tr("إلغاء", "Cancel"),
            onConfirm: { model.refill(medication, amount: amount); close() },
            onDismiss: close
        ) {
            Stepper2(value: "\(amount)", caption: medication.doseUnit == "other" ? nil : Format.unitName(medication.doseUnit), minusEnabled: amount > 1,
                     onMinus: { amount = max(amount - 1, 1) }, onPlus: { amount = min(amount + 1, 1000) })
            ChipRow(items: [10, 20, 30, 60], title: { "\($0)" }, selected: { amount == $0 }, action: { amount = $0 })
        }
    }
}

struct StopDialog: View {
    let medication: Medication
    var onClose: (() -> Void)?
    var onStopped: (() -> Void)?
    @EnvironmentObject private var model: AppModel

    private func close() { if let onClose = onClose { onClose() } else { model.dismissDialog() } }

    var body: some View {
        AfiatiDialog(
            title: tr("إيقاف \(medication.name)؟", "Stop \(medication.name)?"),
            text: tr("لن تصلك تذكيراته بعد الآن، ويبقى ما سجّلته منه في السجل. إذا احتجته لاحقًا أضفه من جديد.",
                     "You won't get its reminders anymore, and what you logged stays in your history. If you need it later, add it again."),
            confirm: tr("إيقاف", "Stop"), destructive: true, dismiss: tr("إلغاء", "Cancel"),
            onConfirm: { model.stopMedication(medication); close(); onStopped?() },
            onDismiss: close
        )
    }
}
