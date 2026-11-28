import SwiftUI

/**
 شاشة تغطي التطبيق: الخدمة متوقفة مؤقتًا، أو يلزم تحديث التطبيق.
 زر الاتصال بالإسعاف ورقم الطوارئ يبقيان، فلا يُحرم الكبير من طلب المساعدة.
 */
struct BlockView: View {
    let block: AppControl.Block
    let updateURL: String
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ZStack {
            AppBackground()
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 14) {
                        LogoView(size: 96).padding(.bottom, 6)
                        if block == .update {
                            Text(tr("يتوفر تحديث لعافيتي", "An Afiati update is available")).style(28, .bold).multilineTextAlignment(.center)
                            Text(tr("حدّث التطبيق ليستمر في العمل. بياناتك وأدويتك محفوظة ولن تتغير.", "Update the app to keep using it. Your data and medicines are saved and won't change."))
                                .style(18, .regular, Brand.secondaryText).multilineTextAlignment(.center)
                        } else {
                            Text(tr("الخدمة متوقفة مؤقتًا", "The service is paused")).style(28, .bold).multilineTextAlignment(.center)
                            Text(tr("نعمل على تحسين عافيتي، وستعود قريبًا. تذكيرات أدويتك المجدولة تبقى تصلك.", "We're improving Afiati and will be back soon. Your scheduled medicine reminders keep coming."))
                                .style(18, .regular, Brand.secondaryText).multilineTextAlignment(.center)
                        }
                        WaveRibbon().frame(height: 110)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 22)
                    .padding(.top, 48)
                }
                VStack(spacing: 10) {
                    if block == .update && !updateURL.isEmpty {
                        PrimaryButton(tr("تحديث التطبيق", "Update the app"), icon: "arrow.down.app.fill") { Links.open(updateURL) }
                    }
                    if !model.emergencyPhone.isEmpty {
                        let who = model.emergencyName.isEmpty ? model.emergencyPhone : model.emergencyName
                        GlassButton(tr("اتصال بـ \(who)", "Call \(who)"), icon: "phone.fill") { Links.dial(model.emergencyPhone) }
                    }
                    GlassButton(tr("اتصال بالإسعاف \(ambulance)", "Call ambulance \(ambulance)"), icon: "crescent", color: Brand.danger) { Links.dial(ambulance) }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .transition(.opacity)
    }
}
