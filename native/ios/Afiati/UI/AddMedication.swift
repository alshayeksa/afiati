import SwiftUI

private var quickSuggestions: [String] {
    [tr("دواء السكري", "Diabetes medicine"), tr("دواء الضغط", "Blood pressure medicine"), tr("مسكن", "Painkiller"),
     tr("دواء القلب", "Heart medicine"), tr("دواء المعدة", "Stomach medicine")]
}

private var categories: [(String, [String])] {
    [
        (tr("فيتامينات", "Vitamins"), [tr("فيتامين د", "Vitamin D"), tr("فيتامين ب", "Vitamin B"), tr("فيتامين سي", "Vitamin C"), tr("كالسيوم", "Calcium"), tr("حديد", "Iron"), tr("مغنيسيوم", "Magnesium")]),
        (tr("الحساسية", "Allergy"), [tr("حبوب الحساسية", "Allergy tablets"), tr("بخاخ الأنف", "Nasal spray"), tr("قطرة الحساسية", "Allergy eye drops"), tr("شراب الحساسية", "Allergy syrup")]),
        (tr("الكوليسترول", "Cholesterol"), [tr("دواء الكوليسترول", "Cholesterol medicine"), tr("منظم الدهون", "Lipid regulator")]),
        (tr("العين", "Eyes"), [tr("قطرة العين", "Eye drops"), tr("قطرة مرطبة", "Lubricating drops"), tr("قطرة ضغط العين", "Glaucoma drops"), tr("مرهم العين", "Eye ointment")]),
        (tr("الربو", "Asthma"), [tr("بخاخ الربو", "Asthma inhaler"), tr("بخاخ توسيع الشعب", "Bronchodilator inhaler"), tr("بخاخ وقائي", "Preventer inhaler")]),
        (tr("المفاصل", "Joints"), [tr("دواء المفاصل", "Joint medicine"), tr("مسكن المفاصل", "Joint painkiller"), tr("كريم موضعي", "Topical cream"), tr("مضاد التهاب", "Anti-inflammatory")]),
    ]
}

private let units = ["حبة", "مل", "قطرة", "بخة"]
private var doseLabels: [String] {
    [tr("الجرعة الأولى", "first dose"), tr("الجرعة الثانية", "second dose"), tr("الجرعة الثالثة", "third dose"), tr("الجرعة الرابعة", "fourth dose")]
}
private let maxFrequency = 4
private let steps = 6

/// أوقات مقترحة موزعة على اليوم حسب عدد المرات
private func defaultTimes(_ count: Int) -> [String] {
    switch count {
    case 1: return ["08:00"]
    case 2: return ["08:00", "20:00"]
    case 3: return ["08:00", "14:00", "20:00"]
    default: return ["08:00", "12:00", "16:00", "20:00"]
    }
}

/**
 إضافة دواء في ست خطوات قصيرة، سؤال واحد في كل خطوة:
 الاسم، الجرعة، التكرار، المواعيد، المخزون، ثم المراجعة.
 */
struct AddMedicationScreen: View {
    let onClose: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var step = 1
    @State private var name = ""
    @State private var dose = 1.0
    @State private var unit = "حبة"
    @State private var repeatType = RepeatType.daily
    @State private var frequency = 1
    @State private var times = ["08:00"]
    @State private var days: [Int] = []
    @State private var trackStock = true
    @State private var stock = 30
    @State private var editingTime: Int?
    @State private var showMore = false
    @State private var category: String?
    @State private var error: String?
    @State private var duplicateOf: String?
    @State private var confirmClose = false

    private var dirty: Bool { !name.trimmingCharacters(in: .whitespaces).isEmpty || step > 1 }

    private func selectRepeat(_ type: String) {
        repeatType = type
        frequency = 1
        times = ["08:00"]
        days = []
        let today = Day.today()
        if type == RepeatType.weekly { days = [today.weekday] }
        if type == RepeatType.monthly { days = [today.day] }
    }

    private func changeFrequency(_ value: Int) {
        frequency = min(max(value, 1), maxFrequency)
        if repeatType == RepeatType.daily {
            times = defaultTimes(frequency)
        } else {
            while days.count > frequency { days.removeFirst() }
        }
    }

    private func toggleDay(_ day: Int) {
        if let index = days.firstIndex(of: day) {
            days.remove(at: index)
        } else {
            if days.count >= frequency { days.removeFirst() }
            days.append(day)
        }
    }

    private func draft() -> MedicationDraft {
        MedicationDraft(
            name: name.trimmingCharacters(in: .whitespacesAndNewlines), doseAmount: dose, doseUnit: unit, repeatType: repeatType,
            repeatDays: repeatType == RepeatType.daily ? [] : days.sorted(), frequency: frequency,
            times: repeatType == RepeatType.daily ? Array(times.prefix(frequency)) : Array(times.prefix(1)),
            stockCount: trackStock ? stock : nil
        )
    }

    private func save(force: Bool = false) {
        let value = draft()
        if !force, let duplicate = model.findDuplicate(value) {
            duplicateOf = duplicate.name
            return
        }
        model.addMedication(value)
        onClose()
    }

    private func daysText() -> String {
        frequency == 1 ? tr("يومًا واحدًا", "one day") : tr("\(frequency) أيام", "\(frequency) days")
    }

    private func next() {
        if step == 1 && name.trimmingCharacters(in: .whitespaces).isEmpty {
            error = tr("اكتب اسم الدواء أولًا، أو اختر من الاقتراحات.", "Type the medicine name first, or pick a suggestion.")
        } else if step == 4 && repeatType != RepeatType.daily && days.count != frequency {
            error = tr("اختر \(daysText()).", "Choose \(daysText()).")
        } else {
            error = nil
        }
        if error != nil { return }
        if step < steps { withAnimation { step += 1 } } else { save() }
    }

    var body: some View {
        FullScreen {
            // الأعلى: إغلاق، العنوان، رقم الخطوة، وشريط التقدم
            HStack(spacing: 12) {
                CircleIconButton(icon: "xmark", label: tr("إغلاق", "Close")) { if dirty { confirmClose = true } else { onClose() } }
                VStack(alignment: .leading, spacing: 0) {
                    Text(tr("إضافة دواء", "Add medicine")).style(22, .bold)
                    Text(tr("الخطوة \(step) من \(steps)", "Step \(step) of \(steps)")).style(15, .regular, Brand.secondaryText)
                }
                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.top, 10)
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(Brand.text.opacity(0.08))
                    Capsule().fill(LinearGradient(colors: [Brand.mint, Brand.glow], startPoint: .leading, endPoint: .trailing))
                        .frame(width: proxy.size.width * CGFloat(step) / CGFloat(steps))
                        .animation(.easeOut, value: step)
                }
            }
            .frame(height: 8)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    stepContent
                    if let error = error { Text(error).style(17, .medium, Brand.danger) }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
            }
            .scrollDismissesKeyboard(.interactively)

            // الأسفل: التالي والسابق
            HStack(spacing: 10) {
                PrimaryButton(step == steps ? tr("حفظ الدواء", "Save medicine") : tr("التالي", "Next"),
                              icon: step == steps ? "checkmark" : "arrow.forward") { next() }
                if step > 1 {
                    GlassButton(tr("السابق", "Back"), icon: "arrow.backward") { error = nil; withAnimation { step -= 1 } }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .overlay {
            if let index = editingTime {
                TimeDialog(
                    title: repeatType == RepeatType.daily ? tr("وقت \(doseLabels[index])", "Time of the \(doseLabels[index])") : tr("وقت الدواء", "Medicine time"),
                    initial: index < times.count ? times[index] : "08:00",
                    onDismiss: { editingTime = nil },
                    onConfirm: { value in
                        if index < times.count { times[index] = value }
                        editingTime = nil
                    }
                )
            } else if let existing = duplicateOf {
                AfiatiDialog(
                    title: tr("الدواء موجود", "Medicine already added"),
                    text: tr("يوجد «\(existing)» بنفس الجرعة والمواعيد. هل تريد إضافته مرة أخرى؟", "“\(existing)” is already there with the same dose and times. Add it again?"),
                    confirm: tr("إضافة على أي حال", "Add anyway"), dismiss: tr("إلغاء", "Cancel"),
                    onConfirm: { duplicateOf = nil; save(force: true) }, onDismiss: { duplicateOf = nil }
                )
            } else if confirmClose {
                AfiatiDialog(
                    title: tr("إلغاء إضافة الدواء؟", "Cancel adding this medicine?"),
                    text: tr("لن يُحفظ ما كتبته.", "What you typed won't be saved."),
                    confirm: tr("إلغاء الإضافة", "Discard"), destructive: true, dismiss: tr("متابعة", "Keep editing"),
                    onConfirm: { confirmClose = false; onClose() }, onDismiss: { confirmClose = false }
                )
            }
        }
    }

    @ViewBuilder
    private var stepContent: some View {
        switch step {
        case 1:
            StepTitle(title: tr("ما اسم الدواء؟", "What is the medicine called?"), message: tr("اكتبه كما هو على العلبة، أو اختر وصفًا قريبًا.", "Type it as written on the box, or pick something close."))
            GlassField(text: Binding(get: { name }, set: { name = $0; error = nil }), placeholder: tr("اسم الدواء", "Medicine name"), size: 22, weight: .medium)
            FieldLabel(tr("اقتراحات سريعة", "Quick suggestions"))
            ChipGrid(items: quickSuggestions, selected: { name == $0 }, action: { name = $0; error = nil })
            GlassButton(showMore ? tr("إخفاء التصنيفات", "Hide categories") : tr("تصنيفات أخرى", "More categories"), icon: showMore ? "chevron.up" : "chevron.down") {
                showMore.toggle()
                category = nil
            }
            if showMore {
                if let active = categories.first(where: { $0.0 == category }) {
                    FieldLabel(tr("اختر النوع · \(active.0)", "Choose the type · \(active.0)"))
                    ChipGrid(items: active.1, selected: { name == $0 }, action: { name = $0; error = nil })
                    GlassButton(tr("رجوع إلى التصنيفات", "Back to categories"), icon: "arrow.backward") { category = nil }
                } else {
                    ChipGrid(items: categories.map { $0.0 }, columns: 3, selected: { _ in false }, action: { category = $0 })
                }
            }
        case 2:
            StepTitle(title: tr("كم تأخذ في كل مرة؟", "How much each time?"), message: tr("الكمية في الجرعة الواحدة.", "The amount in a single dose."))
            Card {
                Stepper2(value: Format.number(dose), caption: Format.dose(dose, unit), minusEnabled: dose > 0.5, plusEnabled: dose < 20,
                         onMinus: { dose = max(dose - 0.5, 0.5) }, onPlus: { dose = min(dose + 0.5, 20) })
            }
            FieldLabel(tr("الوحدة", "Unit"))
            ChipRow(items: units, title: { Format.unitName($0) }, selected: { unit == $0 }, action: { unit = $0 })
        case 3:
            StepTitle(title: tr("كم مرة تأخذه؟", "How often do you take it?"), message: nil)
            HStack(spacing: 8) {
                RepeatOption(title: tr("يوميًا", "Daily"), icon: "sun.max", selected: repeatType == RepeatType.daily) { selectRepeat(RepeatType.daily) }
                RepeatOption(title: tr("أسبوعيًا", "Weekly"), icon: "calendar", selected: repeatType == RepeatType.weekly) { selectRepeat(RepeatType.weekly) }
                RepeatOption(title: tr("شهريًا", "Monthly"), icon: "calendar.badge.clock", selected: repeatType == RepeatType.monthly) { selectRepeat(RepeatType.monthly) }
            }
            Card {
                Text(repeatType == RepeatType.weekly ? tr("كم يومًا في الأسبوع؟", "How many days a week?")
                     : repeatType == RepeatType.monthly ? tr("كم يومًا في الشهر؟", "How many days a month?")
                     : tr("كم مرة في اليوم؟", "How many times a day?")).style(19, .bold)
                Stepper2(value: "\(frequency)", caption: Format.times(frequency), minusEnabled: frequency > 1, plusEnabled: frequency < maxFrequency,
                         onMinus: { changeFrequency(frequency - 1) }, onPlus: { changeFrequency(frequency + 1) })
            }
        case 4:
            if repeatType == RepeatType.daily {
                StepTitle(title: tr("متى تأخذه؟", "When do you take it?"), message: tr("اضغط على الوقت لتغييره.", "Tap a time to change it."))
                ForEach(0..<min(frequency, times.count), id: \.self) { index in
                    TimeRow(label: doseLabels[index], time: times[index]) { editingTime = index }
                }
            } else if repeatType == RepeatType.weekly {
                StepTitle(title: tr("في أي أيام الأسبوع؟", "Which days of the week?"), message: tr("اختر \(daysText()).", "Choose \(daysText())."))
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                    ForEach(Format.weekOrder, id: \.self) { day in
                        Chip(Format.weekdayNames[day] ?? "", selected: days.contains(day)) { toggleDay(day); error = nil }
                    }
                }
                TimeRow(label: tr("وقت الدواء", "Medicine time"), time: times[0]) { editingTime = 0 }
            } else {
                StepTitle(title: tr("في أي أيام الشهر؟", "Which days of the month?"), message: tr("اختر \(daysText()).", "Choose \(daysText())."))
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 7), spacing: 6) {
                    ForEach(1...31, id: \.self) { day in
                        DayNumber(day: day, selected: days.contains(day)) { toggleDay(day); error = nil }
                    }
                }
                .environment(\.layoutDirection, Lang.direction)
                if days.contains(where: { $0 > 28 }) {
                    Text(tr("في الأشهر الأقصر لا تُذكَّر في الأيام غير الموجودة.", "In shorter months, days that don't exist are skipped.")).style(15, .regular, Brand.secondaryText)
                }
                TimeRow(label: tr("وقت الدواء", "Medicine time"), time: times[0]) { editingTime = 0 }
            }
        case 5:
            StepTitle(title: tr("كم لديك منه الآن؟", "How much do you have now?"), message: tr("نذكّرك قبل أن ينفد.", "We'll remind you before it runs out."))
            Toggle(isOn: $trackStock) {
                Text(tr("تنبيهي عند قرب النفاد", "Alert me when it's running low")).style(18, .medium)
            }
            .tint(Brand.mint)
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .glass(radius: 22)
            if trackStock {
                Card {
                    Stepper2(value: "\(stock)", caption: Format.unitName(unit), minusEnabled: stock > 0,
                             onMinus: { stock = max(stock - 1, 0) }, onPlus: { stock = min(stock + 1, 1000) })
                }
                ChipRow(items: [10, 20, 30, 60, 90], title: { "\($0)" }, selected: { stock == $0 }, action: { stock = $0 })
            }
        default:
            let value = draft()
            StepTitle(title: tr("تأكد من البيانات", "Check the details"), message: tr("اضغط «حفظ الدواء» إذا كان كل شيء صحيحًا.", "Tap “Save medicine” if everything is right."))
            Card {
                Text(value.name).style(28, .bold)
                Text(Format.dose(value.doseAmount, value.doseUnit)).style(19, .regular, Brand.secondaryText)
                ReviewLine(icon: "clock", text: Format.schedule(value.repeatType, value.repeatDays, value.times)) { step = 4 }
                ReviewLine(icon: "calendar", text: "\(Format.repeatTitle(value.repeatType)) · \(Format.times(value.frequency))") { step = 3 }
                ReviewLine(icon: "checkmark", text: value.stockCount.map { tr("المخزون: \($0)", "Stock: \($0)") } ?? tr("بدون متابعة المخزون", "Without stock tracking")) { step = 5 }
            }
        }
    }
}

private struct StepTitle: View {
    let title: String
    let message: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).style(30, .bold)
            if let message = message { Text(message).style(17, .regular, Brand.secondaryText) }
        }
        .padding(.top, 6)
    }
}

private struct RepeatOption: View {
    let title: String
    let icon: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 28)).foregroundColor(selected ? Brand.text : Brand.glow)
                Text(title).style(18, .bold).lineLimit(1).minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 18)
            .background(selected ? AnyView(LinearGradient(colors: [Brand.mint.opacity(0.8), Brand.deepGreen.opacity(0.9)], startPoint: .top, endPoint: .bottom)) : AnyView(Color.clear))
            .glass(radius: 22, highlight: selected ? 0.6 : 0.22)
        }
        .buttonStyle(PressStyle())
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct TimeRow: View {
    let label: String
    let time: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text(label).style(19, .bold)
                    Text(tr("اضغط لتغيير الوقت", "Tap to change the time")).style(15, .regular, Brand.secondaryText)
                }
                Spacer()
                HStack(spacing: 8) {
                    Image(systemName: "clock").foregroundColor(Brand.glow)
                    Text(Format.time(time)).style(22, .bold, Brand.glow)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(Brand.glow.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .glass(radius: 22)
        }
        .buttonStyle(PressStyle())
    }
}

private struct DayNumber: View {
    let day: Int
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("\(day)").style(18, .bold, selected ? Brand.ink : Brand.text)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(selected ? AnyView(LinearGradient(colors: [Brand.glow, Brand.mint], startPoint: .top, endPoint: .bottom)) : AnyView(Color.clear))
                .glass(radius: 14, highlight: selected ? 0.6 : 0.18)
        }
        .buttonStyle(PressStyle())
        .accessibilityLabel(tr("يوم \(day)", "Day \(day)"))
    }
}

private struct ReviewLine: View {
    let icon: String
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: icon).foregroundColor(Brand.glow).frame(width: 22)
                Text(text).style(18).frame(maxWidth: .infinity, alignment: .leading)
                Text(tr("تعديل", "Edit")).style(15, .regular, Brand.glow)
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
    }
}

/// اختيار الوقت بأزرار كبيرة بدل الساعة الدوّارة: الساعة، الدقائق (كل 5 دقائق)، صباحًا أو مساءً، وأوقات جاهزة.
struct TimeDialog: View {
    let title: String
    let initial: String
    let onDismiss: () -> Void
    let onConfirm: (String) -> Void
    @State private var hour24: Int
    @State private var minute: Int

    init(title: String, initial: String, onDismiss: @escaping () -> Void, onConfirm: @escaping (String) -> Void) {
        self.title = title
        self.initial = initial
        self.onDismiss = onDismiss
        self.onConfirm = onConfirm
        let start = Schedule.parseTime(initial) ?? (8, 0)
        _hour24 = State(initialValue: start.hour)
        _minute = State(initialValue: start.minute)
    }

    /// أرقام لاتينية دائمًا، لأن الخادم يقبل HH:mm بها فقط
    private var value: String { String(format: "%02d:%02d", hour24, minute) }

    var body: some View {
        let hour12 = hour24 % 12 == 0 ? 12 : hour24 % 12
        let morning = hour24 < 12
        AfiatiDialog(title: title, confirm: tr("تم", "Done"), dismiss: tr("إلغاء", "Cancel"), onConfirm: { onConfirm(value) }, onDismiss: onDismiss) {
            Text(Format.time(hour: hour24, minute: minute)).style(44, .bold, Brand.glow).frame(maxWidth: .infinity)
            MiniStepper(label: tr("الساعة", "Hour"), value: "\(hour12)", onMinus: { hour24 = (hour24 + 23) % 24 }, onPlus: { hour24 = (hour24 + 1) % 24 })
            MiniStepper(label: tr("الدقائق", "Minutes"), value: String(format: "%02d", minute),
                        onMinus: { minute = ((minute / 5) * 5 - 5 + 60) % 60 }, onPlus: { minute = ((minute / 5) * 5 + 5) % 60 })
            HStack(spacing: 8) {
                Chip(tr("صباحًا", "AM"), selected: morning) { if !morning { hour24 -= 12 } }
                Chip(tr("مساءً", "PM"), selected: !morning) { if morning { hour24 += 12 } }
            }
            FieldLabel(tr("أوقات شائعة", "Common times"))
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 3), spacing: 6) {
                ForEach(["07:00", "08:00", "12:00", "14:00", "18:00", "20:00", "22:00"], id: \.self) { preset in
                    Chip(Format.time(preset), selected: value == preset) {
                        if let parsed = Schedule.parseTime(preset) { hour24 = parsed.hour; minute = parsed.minute }
                    }
                }
            }
        }
    }
}

private struct MiniStepper: View {
    let label: String
    let value: String
    let onMinus: () -> Void
    let onPlus: () -> Void

    var body: some View {
        HStack {
            Button(action: onPlus) { Text("+").style(28, .bold, Brand.glow).frame(width: 56, height: 56) }
                .buttonStyle(PressStyle())
                .accessibilityLabel(tr("زيادة \(label)", "Increase \(label)"))
            VStack(spacing: 0) {
                Text(value).style(26, .bold).lineLimit(1)
                Text(label).style(13, .regular, Brand.secondaryText).lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            Button(action: onMinus) { Text("−").style(28, .bold, Brand.glow).frame(width: 56, height: 56) }
                .buttonStyle(PressStyle())
                .accessibilityLabel(tr("إنقاص \(label)", "Decrease \(label)"))
        }
        .glass(radius: 18)
    }
}
