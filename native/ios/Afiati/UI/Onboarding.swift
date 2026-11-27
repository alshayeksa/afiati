import SwiftUI

/// أول فتح: ترحيب قصير، ثم الاسم والجوال. بلا كلمة مرور ولا حساب.
struct OnboardingView: View {
    @EnvironmentObject private var model: AppModel
    @State private var page = 0
    @State private var name = ""
    @State private var phone = ""
    @State private var email = ""
    @State private var gender: String?
    @State private var error: String?
    @State private var saving = false

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if page == 0 {
                        VStack(alignment: .leading, spacing: 8) {
                            LogoView(size: 84)
                            Text(tr("عافيتي", "Afiati")).style(52, .bold).padding(.top, 6)
                            Text(tr("مساعدك الأمين لمواعيد الأدوية، وطمأنينة لعائلتك.", "Your trusted companion for medicine times, and peace of mind for your family."))
                                .style(20, .regular, Brand.secondaryText)
                        }
                        .padding(.horizontal, 20)
                        WaveRibbon().frame(height: 150)
                        VStack(spacing: 10) {
                            Feature(icon: "bell.badge.fill", title: tr("تذكير بكل جرعة في وقتها", "A reminder for every dose on time"), message: tr("وتسجّلها من الإشعار بضغطة واحدة.", "Log it from the notification with one tap."))
                            Feature(icon: "person.3.fill", title: tr("عائلتك تطمئن عليك", "Your family can check on you"), message: tr("بموافقتك، وأنت تختار ما يرونه.", "With your approval, and you choose what they see."))
                            Feature(icon: "wifi.slash", title: tr("يعمل بدون إنترنت", "Works without internet"), message: tr("كل شيء محفوظ على جوالك أولًا.", "Everything is saved on your phone first."))
                        }
                        .padding(.horizontal, 14)
                    } else {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(tr("لنتعرّف عليك", "Let's get to know you")).style(36, .bold)
                            Text(tr("بياناتك على جوالك. لا نطلب كلمة مرور.", "Your data stays on your phone. No password needed.")).style(18, .regular, Brand.secondaryText)
                        }
                        .padding(.horizontal, 20)
                        VStack(spacing: 10) {
                            FieldLabel(tr("اسمك كما يراه أهلك", "Your name as your family sees it"))
                            GlassField(text: Binding(get: { name }, set: { name = $0; error = nil }), placeholder: tr("الاسم", "Name"), size: 21, capitalization: .words)
                            FieldLabel(tr("رقم جوالك", "Your mobile number"))
                            GlassField(text: Binding(get: { phone }, set: { phone = $0; error = nil }), placeholder: "05xxxxxxxx", size: 21, keyboard: .phonePad, ltr: true)
                            FieldLabel(tr("أنا", "I am"))
                            GenderPicker(gender: gender) { gender = $0; error = nil }
                            FieldLabel(tr("البريد الإلكتروني (اختياري)", "Email (optional)"))
                            GlassField(text: Binding(get: { email }, set: { email = $0; error = nil }), placeholder: "name@example.com", keyboard: .emailAddress, ltr: true, capitalization: .never)
                            if let error = error { Text(error).style(17, .medium, Brand.danger).frame(maxWidth: .infinity, alignment: .leading) }
                            Text(tr("يظهر اسمك لمن توافق عليه من عائلتك فقط، ويصلهم رقمك وموقعك فقط حين تطلب المساعدة.",
                                    "Only the family members you approve see your name; they get your number and location only when you ask for help."))
                                .style(15, .regular, Brand.secondaryText)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 4)
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 10)
                    }
                }
                .padding(.top, 20)
                .padding(.bottom, 12)
            }
            .scrollDismissesKeyboard(.interactively)
            Group {
                if page == 0 {
                    PrimaryButton(tr("لنبدأ", "Let's start"), icon: "arrow.forward") { withAnimation { page = 1 } }
                } else {
                    HStack(spacing: 10) {
                        PrimaryButton(saving ? tr("جارٍ الحفظ…", "Saving…") : tr("ابدأ استخدام عافيتي", "Start using Afiati"), icon: "checkmark", enabled: !saving) {
                            error = validateProfile(name: name, phone: phone, email: email) ?? (gender == nil ? tr("اختر: رجل أو امرأة.", "Choose: man or woman.") : nil)
                            if error == nil {
                                saving = true
                                model.createPatient(name: name, phone: phone, email: email.trimmingCharacters(in: .whitespaces).isEmpty ? nil : email, gender: gender)
                                saving = false
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .overlay(alignment: .topTrailing) {
            // اللغة متاحة من أول شاشة
            Button { model.setLanguage(english: !Lang.english) } label: {
                Text(Lang.english ? "العربية" : "English").style(16, .medium, Brand.glow)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .glass(Capsule())
            }
            .buttonStyle(PressStyle())
            .padding(.trailing, 16)
            .padding(.top, 8)
        }
    }
}

private struct Feature: View {
    let icon: String
    let title: String
    let message: String

    var body: some View {
        HStack(spacing: 14) {
            IconBadge(icon: icon, color: Brand.green, size: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).style(18, .bold)
                Text(message).style(16, .regular, Brand.secondaryText)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .glass(radius: 22)
    }
}
