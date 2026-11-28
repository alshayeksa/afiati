import SwiftUI

/// إحصاء يوم واحد
private struct DayStat: Identifiable {
    let day: Day
    let total: Int
    let taken: Int
    var id: Int { day.epochDay }
    var ratio: Double? { total == 0 ? nil : Double(taken) / Double(total) }
}

struct HistoryScreen: View {
    let header: AnyView
    @EnvironmentObject private var model: AppModel
    @State private var now = Date()
    @State private var selected: Day?

    var body: some View {
        let today = model.today
        let byDay = Dictionary(grouping: model.historyLogs) { log in Iso.parse(log.scheduledAt).map { Day($0) } ?? today }
        let nowIso = Iso.format(now)
        let days: [DayStat] = (0..<30).map { offset in
            let day = today.adding(days: -offset)
            // الجرعات التي لم يحن موعدها لا تدخل في نسبة الالتزام
            let due = (byDay[day] ?? []).filter { $0.status == DoseStatus.taken || $0.scheduledAt <= nowIso }
            return DayStat(day: day, total: due.count, taken: due.filter { $0.status == DoseStatus.taken }.count)
        }
        let chosen = selected ?? today
        let chosenLogs = byDay[chosen] ?? []
        TabList(header: header) {
            BigTitle(title: tr("السجل", "History"), subtitle: tr("التزامك في آخر 30 يومًا", "Your adherence over the last 30 days"))
            Summary(days: days)
            SectionTitle(text: Format.monthTitle(chosen))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(days) { stat in
                        DayCell(stat: stat, selected: stat.day == chosen, isToday: stat.day == today) { selected = stat.day }
                    }
                }
                .padding(.horizontal, 14)
            }
            SectionTitle(text: chosen == today ? tr("اليوم", "Today") : "\(Format.weekdayNames[chosen.weekday] ?? "") \(chosen.day)", top: 14)
            if chosenLogs.isEmpty {
                Text(tr("لا توجد جرعات مسجلة في هذا اليوم.", "No doses were logged on this day.")).style(17, .regular, Brand.secondaryText)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(22)
                    .glass(radius: 24)
                    .padding(.horizontal, 14)
            } else {
                ForEach(chosenLogs) { log in
                    let state: DoseState = chosen == today ? doseState(log, now: now, takenOverride: model.isTaken(log))
                        : (log.status == DoseStatus.taken ? .taken : .missed)
                    DoseRow(log: log, state: state, onTake: nil)
                }
            }
        }
        .modifier(NowTicker(now: $now))
    }
}

private struct Summary: View {
    let days: [DayStat]

    private func percent(_ list: [DayStat]) -> Int? {
        let total = list.reduce(0) { $0 + $1.total }
        return total == 0 ? nil : list.reduce(0) { $0 + $1.taken } * 100 / total
    }

    var body: some View {
        let week = percent(Array(days.prefix(7)))
        let month = percent(days)
        // أيام متتالية بالتزام كامل، تبدأ من أمس (اليوم لم ينته بعد) ويُضاف اليوم إن اكتمل
        let todayComplete = days.first.map { $0.total > 0 && $0.taken == $0.total } ?? false
        var streak = 0
        for stat in days.dropFirst() {
            guard stat.total > 0 && stat.taken == stat.total else { break }
            streak += 1
        }
        let total = streak + (todayComplete ? 1 : 0)
        return HStack(spacing: 10) {
            StatCard(label: tr("آخر 7 أيام", "Last 7 days"), value: week.map { tr("\($0)٪", "\($0)%") } ?? "—")
            StatCard(label: tr("آخر 30 يومًا", "Last 30 days"), value: month.map { tr("\($0)٪", "\($0)%") } ?? "—")
            StatCard(label: tr("أيام كاملة متتالية", "Full days in a row"), value: "\(total)")
        }
        .padding(.horizontal, 14)
    }
}

private struct StatCard: View {
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value).style(28, .bold, Brand.glow).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).style(14, .regular, Brand.secondaryText).multilineTextAlignment(.center).lineLimit(2).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 10)
        .padding(.vertical, 16)
        .glass(radius: 22)
    }
}

private struct DayCell: View {
    let stat: DayStat
    let selected: Bool
    let isToday: Bool
    let action: () -> Void

    var body: some View {
        let dot: Color = {
            guard let ratio = stat.ratio else { return Brand.text.opacity(0.15) }
            if ratio >= 1 { return Brand.glow }
            if ratio >= 0.5 { return Brand.warning }
            return Brand.danger
        }()
        Button(action: action) {
            VStack(spacing: 2) {
                Text(isToday ? tr("اليوم", "Today") : Format.shortWeekday(stat.day)).style(13, .medium, selected ? Brand.text : Brand.secondaryText).lineLimit(1)
                Text("\(stat.day.day)").style(24, .bold)
                Circle().fill(dot).frame(width: 10, height: 10).padding(.top, 4)
            }
            .frame(width: 64)
            .padding(.vertical, 12)
            .background(selected ? AnyView(LinearGradient(colors: [Brand.mint.opacity(0.8), Brand.deepGreen.opacity(0.9)], startPoint: .top, endPoint: .bottom)) : AnyView(Color.clear))
            .glass(radius: 20, highlight: selected ? 0.6 : (isToday ? 0.4 : 0.18))
        }
        .buttonStyle(PressStyle())
        .accessibilityLabel(tr("عرض يوم \(stat.day.day)", "Show day \(stat.day.day)"))
    }
}
