// Builds Afiati's static website into worker/public: home, privacy, support, terms and account
// deletion, in Arabic and English. The Worker serves it on afiati.alshaye.ai (wrangler.jsonc "assets").
// Run: python site/prepare_assets.py && node site/build.mjs
// No JavaScript and no third-party requests: fonts and images come from the same domain.
import { copyFileSync, mkdirSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const out = join(root, "worker", "public");
const SITE = "https://afiati.alshaye.ai";

// Contact shown on the privacy, support and deletion pages
const CONTACT_EMAIL = "support@alshaye.ai";
const DEV_SITE = "https://alshaye.ai";
// Store links: leave empty until the app is published there ("coming soon" is shown instead)
const APP_STORE_URL = "";
const PLAY_URL = "";
const UPDATED = { ar: "28 نوفمبر 2026", en: "November 28, 2026" };

const paths = {
  ar: { home: "/", privacy: "/privacy/", support: "/support/", terms: "/terms/", delete: "/delete-account/" },
  en: { home: "/en/", privacy: "/en/privacy/", support: "/en/support/", terms: "/en/terms/", delete: "/en/delete-account/" },
};

const mail = `<a href="mailto:${CONTACT_EMAIL}">${CONTACT_EMAIL}</a>`;

const common = {
  ar: {
    dir: "rtl", name: "عافيتي", other: "English", otherLang: "en",
    nav: { features: "المزايا", family: "العائلة", privacy: "الخصوصية", support: "الدعم", terms: "شروط الاستخدام", delete: "حذف الحساب" },
    by: "تطوير", dev: "شايع الشايع", role: "مطوّر وخبير ذكاء اصطناعي",
    rights: "جميع الحقوق محفوظة.", updated: "آخر تحديث", skip: "تخطَّ إلى المحتوى",
    contact: `راسلنا على ${mail}، أو عبر موقع المطوّر <a href="${DEV_SITE}">alshaye.ai</a>.`,
  },
  en: {
    dir: "ltr", name: "Afiati", other: "العربية", otherLang: "ar",
    nav: { features: "Features", family: "Family", privacy: "Privacy", support: "Support", terms: "Terms of Use", delete: "Delete account" },
    by: "By", dev: "Shaye Alshaye", role: "Developer & AI Expert",
    rights: "All rights reserved.", updated: "Last updated", skip: "Skip to content",
    contact: `Email us at ${mail}, or reach us through the developer's website <a href="${DEV_SITE}">alshaye.ai</a>.`,
  },
};

// Material icons (Apache 2.0)
const ICONS = {
  bell: "M7.58 4.08 6.15 2.65C3.75 4.48 2.17 7.3 2.03 10.5h2a8.445 8.445 0 0 1 3.55-6.42zm12.39 6.42h2c-.15-3.2-1.73-6.02-4.12-7.85l-1.42 1.43a8.495 8.495 0 0 1 3.54 6.42zM18 11c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2v-5zm-6 11c.14 0 .27-.01.4-.04.65-.14 1.18-.58 1.44-1.18.1-.24.15-.5.15-.78h-4c.01 1.1.9 2 2.01 2z",
  text: "M2.5 4v3h5v12h3V7h5V4h-13zm19 5h-9v3h3v7h3v-7h3V9z",
  heart: "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z",
  chart: "M3.5 18.49l6-6.01 4 4L22 6.92l-1.41-1.41-7.09 7.97-4-4L2 16.99z",
  pharmacy: "M21 5h-2.64l1.14-3.14L17.15 1l-1.46 4H3v2l2 6-2 6v2h18v-2l-2-6 2-6V5zm-5 9h-3v3h-2v-3H8v-2h3V9h2v3h3v2z",
  offline: "M23.64 7c-.45-.34-4.93-4-11.64-4-1.5 0-2.89.19-4.15.48L18.18 13.8 23.64 7zm-6.6 8.22L3.27 1.44 2 2.72l2.05 2.06C1.91 5.76.59 6.82.36 7l11.63 14.49.01.01.01-.01 3.9-4.86 3.32 3.32 1.27-1.27-3.46-3.46z",
};
const icon = (name) => `<span class="icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="${ICONS[name]}"/></svg></span>`;

// ---------- Home ----------
const home = {
  ar: {
    title: "عافيتي — دواؤك في وقته، وعائلتك معك",
    description: "عافيتي يذكّر كبار السن بكل جرعة في وقتها، ويطمئن أبناءهم: يرون التزامهم بالدواء بموافقتهم، ويصلهم طلب المساعدة مع الموقع بضغطة واحدة.",
    eyebrow: "لكبار السن وأبنائهم",
    tagline: "دواؤك في وقته، وعائلتك معك",
    lead: "تذكير بسيط وواضح بكل جرعة، وطمأنينة لأبنائك وأقاربك: يرون التزامك بالدواء بموافقتك، ويصلهم طلب مساعدتك مع موقعك بضغطة واحدة.",
    appStore: "حمّل من App Store", play: "احصل عليه من Google Play", soonApple: "App Store", soonPlay: "Google Play", soon: "قريبًا",
    heroAlt: "الشاشة الرئيسية في عافيتي: جرعات اليوم",
    familyTitle: "عائلتك معك، وإن بعدت المسافة",
    familyLead: "عافيتي يصل الكبير بأبنائه وأقاربه دون مكالمات قلقة ولا أسئلة متكررة: هم يطمئنون، وهو يعيش يومه باستقلال.",
    family: [
      ["يطمئنون على جرعاتك", "يرى ابنك أو ابنتك من جوالهم ما أخذته اليوم وما بقي، بموافقتك وبالقدر الذي تختاره. وإن فاتتك جرعة ساعةً يصلهم تنبيه.", "mom-day", "قريب يتابع أدوية والدته اليوم"],
      ["طلب المساعدة يصلهم فورًا", "ضغطة واحدة ترسل تنبيهًا عاجلًا لكل من يتابعك مع موقعك على الخريطة، ويتكرر حتى يرد أحدهم. ويستطيعون الاتصال بك مباشرة.", "alert", "تنبيه طلب مساعدة يصل إلى الابن مع الموقع"],
      ["«أنا قادم» تطمئنك", "حين يرد أحدهم يصلك اسمه بنغمة هادئة، ويعرف بقية العائلة أن هناك من تولّى الأمر. وزر الإسعاف 997 دائمًا أمامك.", "help", "رسالة محمد قادم إليك على جوال الأم"],
    ],
    stepsTitle: "كيف تربط عائلتك",
    steps: [
      ["ادعُ قريبك", "من «العائلة» اضغط «دعوة فرد من العائلة» وأرسل الرمز المكوّن من 6 أرقام، أو اعرض له رمز QR."],
      ["وافق واختر ما يراه", "يُدخل قريبك الرمز في تطبيقه، فتوافق أنت وتحدد ما يراه: كل شيء، أو أخذتَ أم لا فقط، أو تنبيهات التأخير فقط."],
      ["اطمئنان متبادل", "يتابعون يومك ويرسلون لك تذكيرًا لطيفًا عند الحاجة، وتستطيع إيقاف المتابعة متى شئت."],
    ],
    featuresTitle: "كل ما تحتاجه، بلا تعقيد",
    features: [
      ["bell", "تذكير لا يفوتك", "إشعار في الوقت الدقيق تسجّل منه الجرعة بضغطة. وفي التطبيق تومض بطاقة الجرعة مع نغمة لمدة 15 دقيقة حتى تسجّلها."],
      ["text", "مصمّم لكبار السن", "خط كبير وأزرار واضحة وتباين مريح، بالعربية والإنجليزية، مع التاريخ الهجري وخيار تكبير الخط."],
      ["heart", "قياس النبض بالكاميرا", "ضع إصبعك على الكاميرا الخلفية فتظهر قراءة تقديرية لنبضك خلال ثوانٍ. يتم القياس داخل جوالك ولا تُحفظ أي صورة."],
      ["chart", "العلامات الحيوية", "سجّل الضغط والسكر والحرارة وتابع قراءاتك مع الوقت. القراءات محفوظة على جوالك."],
      ["pharmacy", "تنبيه قبل نفاد الدواء", "يذكّرك حين يقارب المخزون على النفاد، ويُخبر من يتابعك ليشتريه لك."],
      ["offline", "يعمل بدون إنترنت", "التذكيرات وتسجيل الجرعات محفوظة على جوالك أولًا، وتُزامَن مع عائلتك حين يعود الاتصال."],
    ],
    shotsTitle: "من داخل التطبيق",
    shots: [["home", "جرعات اليوم"], ["meds", "أدويتي"], ["family", "من يتابعني"]],
    privacyTitle: "خصوصيتك بيدك",
    privacyText: "عافيتي لا يطلب كلمة مرور ولا يعرض إعلانات ولا يبيع بياناتك. أنت من يوافق على من يتابعك وتحدد ما يراه.",
    points: ["بلا كلمة مرور", "بلا إعلانات", "موقعك فقط حين تطلب المساعدة", "احذف حسابك متى شئت"],
    privacyLink: "اقرأ سياسة الخصوصية",
    note: "<b>تنبيه طبي:</b> عافيتي مساعد للتذكير والمتابعة، ولا يقدّم تشخيصًا ولا يغني عن استشارة الطبيب أو الصيدلي. قراءات النبض تقديرية. في حالات الطوارئ اتصل بالإسعاف 997 مباشرة.",
  },
  en: {
    title: "Afiati — Your medicine on time, your family by your side",
    description: "Afiati reminds older adults of every dose on time and reassures their families: relatives see their medicine adherence with their consent, and get help requests with the location in one tap.",
    eyebrow: "For older adults and their families",
    tagline: "Your medicine on time, your family by your side",
    lead: "Clear, simple reminders for every dose, and peace of mind for your children and relatives: with your consent they see how you're doing with your medicines, and your help request reaches them with your location in one tap.",
    appStore: "Download on the App Store", play: "Get it on Google Play", soonApple: "App Store", soonPlay: "Google Play", soon: "Coming soon",
    heroAlt: "Afiati's home screen: today's doses",
    familyTitle: "Your family with you, however far away",
    familyLead: "Afiati connects older adults with their children and relatives without worried calls or repeated questions: they get peace of mind, and you live your day independently.",
    family: [
      ["They know you took your medicine", "Your son or daughter sees on their phone what you took today and what's left, with your consent and only as much as you choose. If a dose is an hour late, they get an alert.", "mom-day", "A relative checks their mother's medicines for today"],
      ["Your help request reaches them instantly", "One tap sends an urgent alert with your location on the map to everyone who follows you, repeated until someone answers. They can call you right away.", "alert", "A help request reaches the son with the location"],
      ["“I'm coming” puts you at ease", "When someone answers, you hear their name with a calm chime, and the rest of the family knows someone is on it. The 997 ambulance button is always in front of you.", "help", "Mohammed is coming to you, on the mother's phone"],
    ],
    stepsTitle: "How to connect your family",
    steps: [
      ["Invite a relative", "In “Family”, tap “Invite a family member” and send the 6-digit code, or show them the QR code."],
      ["Approve and choose what they see", "Your relative enters the code in their app. You approve and choose what they see: everything, taken or not only, or missed-dose alerts only."],
      ["Peace of mind both ways", "They follow your day and can send you a gentle reminder. You can stop sharing at any time."],
    ],
    featuresTitle: "Everything you need, nothing complicated",
    features: [
      ["bell", "Reminders you won't miss", "An on-time notification you can mark the dose from in one tap. In the app, the dose card flashes with a chime for 15 minutes until you mark it."],
      ["text", "Designed for older adults", "Large text, clear buttons and comfortable contrast, in Arabic and English, with the Hijri date and a larger-text option."],
      ["heart", "Pulse with the camera", "Place your finger on the back camera to get an estimate of your pulse in seconds. It's measured on your phone and no photo is saved."],
      ["chart", "Vital signs", "Log blood pressure, blood sugar and temperature and follow your readings over time. Readings stay on your phone."],
      ["pharmacy", "Before you run out", "Reminds you when a medicine is running low, and tells the people who follow you so they can buy it."],
      ["offline", "Works offline", "Reminders and dose logging are saved on your phone first, and sync with your family when you're back online."],
    ],
    shotsTitle: "Inside the app",
    shots: [["home", "Today's doses"], ["meds", "My medicines"], ["family", "Who follows me"]],
    privacyTitle: "Your privacy, your choice",
    privacyText: "Afiati asks for no password, shows no ads and never sells your data. You approve who follows you and choose what they see.",
    points: ["No password", "No ads", "Location only when you ask for help", "Delete your account anytime"],
    privacyLink: "Read the privacy policy",
    note: "<b>Medical notice:</b> Afiati helps with reminders and follow-up. It does not diagnose and does not replace your doctor or pharmacist. Pulse readings are estimates. In an emergency, call 997 directly.",
  },
};

// ---------- Privacy policy ----------
const privacy = {
  ar: {
    title: "سياسة الخصوصية — عافيتي",
    description: "سياسة الخصوصية لتطبيق عافيتي لتذكير الأدوية وطمأنة العائلة.",
    heading: "سياسة الخصوصية",
    intro: "عافيتي تطبيق لتذكير الأدوية يصل كبار السن بعائلاتهم. نجمع أقل قدر ممكن من البيانات، ونستخدمه لتشغيل التطبيق فقط. توضّح هذه الصفحة ما يعالجه التطبيق وخادمه، ومن يراه، وكيف تحذفه.",
    sections: [
      ["باختصار", [
        "لا كلمة مرور، ولا إعلانات، ولا تتبّع إعلاني، ولا أدوات تحليل من أطراف أخرى، ولا نبيع أي بيانات.",
        "أقاربك لا يرون شيئًا إلا بعد موافقتك، وبالقدر الذي تختاره.",
        "موقعك لا يُرسل إلا حين تطلب المساعدة.",
        "تحذف حسابك وكل بياناتك من داخل التطبيق متى شئت.",
      ]],
      ["ما تُدخله في التطبيق", [
        "ملفك: الاسم ورقم الجوال والجنس (لتُكتب الرسائل بصيغة صحيحة)، والبريد الإلكتروني إن أضفته وهو اختياري.",
        "أدويتك: الاسم والجرعة والمواعيد والتكرار والمخزون والملاحظات، وسجل الجرعات (أُخذت، أو فاتت).",
        "رقم للطوارئ إن أضفته: يبقى في جوالك فقط.",
        "قراءات النبض والعلامات الحيوية (الضغط والسكر والحرارة): تبقى في جوالك فقط ولا تُرفع إلى خادمنا.",
      ]],
      ["ما يُحفظ في خادمنا ولماذا", [
        "ملفك وأدويتك وسجل جرعاتك: لتصل إلى أقاربك الذين وافقت عليهم، ولتُنسخ احتياطيًا فتعود إذا انقطع الاتصال.",
        "معرّف عشوائي للجهاز وبصمة مشفّرة (hash) لمفتاحه السري: ليثبت التطبيق أن الحساب لك. لا نحتفظ بالمفتاح نفسه.",
        "رمز الإشعارات لجهازك: لنرسل لك ولعائلتك التنبيهات (Apple Push Notification service في الآيفون، وFirebase Cloud Messaging من Google في أندرويد).",
        "روابط العائلة ومستوى ما يراه كل قريب، ورموز الدعوة (صالحة 24 ساعة).",
        "طلبات المساعدة: وقتها والسبب إن كتبته، والموقع ودقته إن سمحت بالموقع، ومن ردّ عليها.",
        "الإشعارات التي أُرسلت إليك (العنوان والنص)، لتظهر في قائمة الإشعارات في التطبيق.",
        "المنطقة الزمنية ولغة الواجهة: لتصل الرسائل بلغتك وفي وقت مناسب، فلا نرسل تنبيهات تحتمل التأجيل في الليل.",
        "حالة الخدمة وأحدث إصدار مطلوب: يسأل عنها التطبيق عند فتحه، دون بيانات شخصية.",
      ]],
      ["ما يراه أقاربك", [
        "لا يتابعك أحد إلا إذا أدخل رمز دعوة أرسلته أنت، ثم وافقت عليه.",
        "تختار لكل قريب واحدًا من ثلاثة: «كل شيء» (أسماء الأدوية والجرعات ومواعيدها)، أو «أخذت أم لا فقط» (دون أسماء الأدوية)، أو «تنبيهات التأخير فقط».",
        "إذا بقيت جرعة دون تسجيل ساعة بعد موعدها يصل تنبيه لمن يتابعك. وإذا قارب دواء على النفاد يصل تنبيه لمن يرى «كل شيء».",
        "حين تضغط «أحتاج مساعدة» يصل التنبيه لكل من يتابعك أيًّا كان مستواه، ومعه اسمك ورقم جوالك والسبب إن كتبته، وموقعك إن سمحت به.",
        "يرى أقاربك اسمك فقط في قائمة العائلة، ولا يرون بريدك. وتستطيع إزالة أي قريب متى شئت.",
      ]],
      ["الموقع", [
        "يُستخدم الموقع حين تطلب المساعدة فقط، وأثناء استخدامك التطبيق، ولا يُتابع في الخلفية.",
        "يُرسل الموقع لأقاربك المتابعين مع طلب المساعدة كرابط على خرائط Google. فتح الرابط يتم عبر Google وفق سياستها.",
        "إذا لم تسمح بالموقع يصل طلب المساعدة دونه.",
      ]],
      ["الكاميرا وقياس النبض", [
        "تُستخدم الكاميرا الخلفية والفلاش لقياس النبض فقط، وحين تبدأ القياس.",
        "يتم التحليل داخل جوالك، ولا تُحفظ أي صورة ولا تُرسل. ونتيجة القياس تُحفظ في جوالك فقط.",
      ]],
      ["إحصاءات الاستخدام", [
        "نعدّ المستخدمين والأجهزة النشطة يوميًا وتوزيعها على الدول، لنعرف حجم الاستخدام.",
        "نعرف الدولة من رمز دولة توفّره Cloudflare مع كل اتصال، ونحفظ الرمز فقط (مثل SA) مع الجهاز. لا نحفظ عنوان IP.",
        "لحماية الخدمة من الإساءة نعدّ الحسابات الجديدة من كل شبكة باستخدام بصمة مشفّرة لعنوان IP لا يمكن استرجاع العنوان منها، وتُحذف خلال يومين.",
      ]],
      ["الأذونات", [
        "الإشعارات: لتذكيرك بالدواء ولتصلك تنبيهات العائلة.",
        "التنبيهات في الوقت الدقيق والتشغيل عند بدء الجوال (أندرويد): ليصل التذكير في موعده، وتُعاد جدولة التذكيرات بعد إعادة التشغيل.",
        "الاهتزاز: مع التنبيهات.",
        "الكاميرا: لقياس النبض فقط.",
        "الموقع (أثناء الاستخدام): مع طلب المساعدة فقط.",
        "التحديث في الخلفية (الآيفون): لمزامنة جرعاتك مع عائلتك دوريًا.",
      ]],
      ["الأطراف الأخرى", [
        "خادمنا يعمل على شبكة Cloudflare، التي تعالج بيانات الاتصال التقنية (مثل عنوان IP) لتوصيل الطلبات وحماية الخدمة، وقد تحتفظ بسجلات تشغيل تقنية لأيام قليلة، وفق سياسة Cloudflare.",
        "Apple وGoogle توصلان الإشعارات إلى جهازك وفق سياستيهما. لا نستخدم من Firebase غير الإشعارات: لا تحليلات ولا تقارير أعطال.",
        "متاجر التطبيقات (Apple وGoogle) تعالج بيانات التنزيل والتحديث وفق سياساتها.",
        "لا نشارك بياناتك مع أي جهة طبية أو تجارية. وإن أتحنا مستقبلًا وصول طلب المساعدة إلى جهة إسعاف فسيكون ذلك اختياريًا ومتوقفًا حتى تفعّله أنت صراحة.",
      ]],
      ["الاحتفاظ والحذف", [
        "تبقى بياناتك في الخادم ما دام حسابك قائمًا.",
        "من داخل التطبيق: «الإعدادات» ثم «حذف حسابي وبياناتي». يُحذف نهائيًا من الخادم ملفك وأجهزتك وأدويتك وسجل جرعاتك وطلبات المساعدة والإشعارات وروابط العائلة، ويُحذف كل شيء من جوالك، ويعود التطبيق إلى البداية.",
        "الرسائل التي وصلت إلى جوالات أقاربك سابقًا تبقى على جوالاتهم.",
        "حذف التطبيق من الجوال يحذف ما فيه، لكنه لا يحذف حسابك من الخادم. استخدم «حذف حسابي وبياناتي» قبل حذف التطبيق، أو اطلب الحذف من صفحة <a href=\"/delete-account/\">حذف الحساب</a>.",
      ]],
      ["الأمان", [
        "الاتصال بين التطبيق والخادم مشفّر (HTTPS). كل حساب لا يصل إلا إلى بياناته وبيانات من وافقوا على متابعتهم له.",
      ]],
      ["الأطفال", [
        "التطبيق غير موجّه للأطفال دون 13 عامًا، ولا نجمع عن قصد بيانات منهم.",
      ]],
      ["تغييرات هذه السياسة", [
        "إذا غيّرنا هذه السياسة سننشر النسخة الجديدة في هذه الصفحة مع تاريخ التحديث.",
      ]],
    ],
  },
  en: {
    title: "Privacy Policy — Afiati",
    description: "Privacy policy for Afiati, the medicine reminder app that keeps families reassured.",
    heading: "Privacy Policy",
    intro: "Afiati is a medicine reminder app that connects older adults with their families. We collect as little data as possible and use it only to run the app. This page explains what the app and its server process, who can see it, and how to delete it.",
    sections: [
      ["In short", [
        "No password, no ads, no advertising tracking, no third-party analytics tools, and we never sell any data.",
        "Your relatives see nothing until you approve them, and only as much as you choose.",
        "Your location is sent only when you ask for help.",
        "You can delete your account and all your data from inside the app at any time.",
      ]],
      ["What you enter in the app", [
        "Your profile: name, phone number and gender (so messages are worded correctly), and your email address if you add it, which is optional.",
        "Your medicines: name, dose, times, repeat, stock and notes, and your dose history (taken or missed).",
        "An emergency number, if you add one: it stays on your phone only.",
        "Pulse readings and vital signs (blood pressure, blood sugar, temperature): they stay on your phone only and are not uploaded to our server.",
      ]],
      ["What is stored on our server, and why", [
        "Your profile, medicines and dose history: so they reach the relatives you approved, and so they are backed up and come back after a lost connection.",
        "A random device identifier and a hash of its secret key: so the app can prove the account is yours. We do not keep the key itself.",
        "Your device's notification token: so we can send alerts to you and your family (Apple Push Notification service on iPhone, and Google's Firebase Cloud Messaging on Android).",
        "Family links and what each relative may see, and invite codes (valid for 24 hours).",
        "Help requests: when they were made, the reason if you wrote one, the location and its accuracy if you allowed location, and who answered.",
        "Notifications sent to you (title and text), so they appear in the notifications list in the app.",
        "Your time zone and interface language: so messages reach you in your language at a suitable time, and alerts that can wait are not sent at night.",
        "Service status and the minimum required version: the app asks for them when it opens, with no personal data.",
      ]],
      ["What your relatives see", [
        "No one follows you unless they enter an invite code you sent them, and you then approve them.",
        "For each relative you choose one of three: “Everything” (medicine names, doses and times), “Taken or not only” (without medicine names), or “Missed-dose alerts only”.",
        "If a dose is still not marked an hour after its time, the people who follow you get an alert. If a medicine is running low, people who see “Everything” get an alert.",
        "When you tap “I need help”, everyone who follows you is alerted whatever their level, with your name, phone number, your reason if you wrote one, and your location if you allowed it.",
        "Relatives see only your name in the family list, never your email. You can remove any relative at any time.",
      ]],
      ["Location", [
        "Location is used only when you ask for help, while you're using the app. It is never tracked in the background.",
        "It is sent to the relatives who follow you with the help request, as a Google Maps link. Opening the link goes through Google under its own policy.",
        "If you don't allow location, the help request is sent without it.",
      ]],
      ["Camera and pulse measurement", [
        "The back camera and flash are used only to measure your pulse, and only when you start a measurement.",
        "The analysis happens on your phone. No photo is saved or sent, and the result is saved on your phone only.",
      ]],
      ["Usage statistics", [
        "We count users and daily active devices and how they are spread across countries, to understand how much the app is used.",
        "We learn the country from a country code that Cloudflare provides with each connection, and store only the code (such as SA) with the device. We do not store IP addresses.",
        "To protect the service from abuse, we count new accounts per network using a hash of the IP address that cannot be turned back into the address, and it is deleted within two days.",
      ]],
      ["Permissions", [
        "Notifications: to remind you about your medicines and deliver family alerts.",
        "Exact alarms and start at boot (Android): so reminders arrive on time and are scheduled again after a restart.",
        "Vibration: with alerts.",
        "Camera: for pulse measurement only.",
        "Location (while using the app): with help requests only.",
        "Background refresh (iPhone): to sync your doses with your family periodically.",
      ]],
      ["Other parties", [
        "Our server runs on Cloudflare's network, which processes technical connection data (such as IP addresses) to deliver requests and protect the service, and may keep technical operating logs for a few days, under Cloudflare's own policy.",
        "Apple and Google deliver notifications to your device under their own policies. We use Firebase for notifications only: no analytics and no crash reporting.",
        "App stores (Apple and Google) process download and update data under their own policies.",
        "We do not share your data with any medical or commercial organisation. If we later let help requests reach an ambulance service, it will be optional and off until you explicitly turn it on.",
      ]],
      ["Retention and deletion", [
        "Your data stays on the server for as long as your account exists.",
        "Inside the app: “Settings”, then “Delete my account and data”. This permanently deletes your profile, devices, medicines, dose history, help requests, notifications and family links from the server, deletes everything from your phone, and returns the app to the start.",
        "Messages that already reached your relatives' phones stay on their phones.",
        "Deleting the app from your phone deletes what's on the phone, but not your account on the server. Use “Delete my account and data” before deleting the app, or request deletion on the <a href=\"/en/delete-account/\">Delete account</a> page.",
      ]],
      ["Security", [
        "The connection between the app and the server is encrypted (HTTPS). Each account can reach only its own data and the data of people who approved it to follow them.",
      ]],
      ["Children", [
        "The app is not directed to children under 13, and we do not knowingly collect data from them.",
      ]],
      ["Changes to this policy", [
        "If we change this policy, we will publish the new version on this page with the updated date.",
      ]],
    ],
  },
};

// ---------- Terms of use ----------
const terms = {
  ar: {
    title: "شروط الاستخدام — عافيتي",
    description: "شروط استخدام تطبيق عافيتي.",
    heading: "شروط الاستخدام",
    intro: "باستخدامك تطبيق عافيتي توافق على هذه الشروط.",
    sections: [
      ["الخدمة", [
        "عافيتي تطبيق لتذكير مواعيد الأدوية وتسجيلها، ومشاركة ذلك مع أقارب توافق عليهم، وإرسال طلب مساعدة لهم.",
        "التطبيق مجاني، ولا يعرض إعلانات.",
      ]],
      ["ليس جهازًا طبيًا", [
        "عافيتي لا يقدّم تشخيصًا ولا علاجًا ولا نصيحة طبية، ولا يغني عن الطبيب أو الصيدلي. اتبع دائمًا تعليمات طبيبك ونشرة الدواء.",
        "قراءات النبض بالكاميرا تقديرية، والعلامات الحيوية التي تسجّلها للمتابعة فقط.",
        "أنت مسؤول عن صحة الأدوية والجرعات والمواعيد التي تُدخلها.",
      ]],
      ["الطوارئ", [
        "زر «أحتاج مساعدة» ينبّه أقاربك المتابعين فقط، ولا يتصل بالإسعاف ولا بأي جهة رسمية.",
        "وصول التنبيهات يعتمد على الإنترنت وإعدادات الجوال وخدمات الإشعارات، فلا نضمن وصولها في كل الأحوال. في الحالات الطارئة اتصل بالإسعاف 997 مباشرة.",
      ]],
      ["العمر", [
        "يجب أن يكون عمرك 13 عامًا على الأقل لاستخدام التطبيق، أو السن الأدنى المطلوب في بلدك إن كان أعلى.",
      ]],
      ["مسؤوليتك", [
        "أدخل بيانات صحيحة، ولا تتابع أحدًا إلا بعلمه وموافقته. المتابعة لا تتم أصلًا إلا بموافقة صاحب الحساب.",
        "استخدم ما تراه من بيانات أقاربك للاطمئنان عليهم فقط، ولا تشاركها مع غيرك.",
        "لا تحاول تعطيل الخدمة أو اختراقها أو استخدامها بطريقة آلية.",
      ]],
      ["إخلاء المسؤولية", [
        "نقدّم الخدمة كما هي، ونعمل على استمرارها ودقتها، لكننا لا نضمن أن تعمل دائمًا دون انقطاع أو خطأ.",
        "بالقدر الذي يسمح به النظام، لسنا مسؤولين عن أي ضرر ينتج عن الاعتماد على التطبيق بدل الرعاية الطبية، أو عن تأخر تنبيه أو عدم وصوله.",
      ]],
      ["التغييرات والإنهاء", [
        "قد نحدّث هذه الشروط، وننشر النسخة الجديدة في هذه الصفحة مع تاريخ التحديث. استمرارك في الاستخدام بعد التحديث موافقة عليها.",
        "تستطيع التوقف عن استخدام عافيتي متى شئت، وحذف حسابك من «الإعدادات» ثم «حذف حسابي وبياناتي».",
        "قد نوقف حسابًا يخالف هذه الشروط.",
        "تخضع هذه الشروط لأنظمة المملكة العربية السعودية.",
      ]],
    ],
  },
  en: {
    title: "Terms of Use — Afiati",
    description: "Terms of use for Afiati.",
    heading: "Terms of Use",
    intro: "By using Afiati you agree to these terms.",
    sections: [
      ["The service", [
        "Afiati is an app for medicine reminders and dose logging, for sharing them with relatives you approve, and for sending them a help request.",
        "The app is free and shows no ads.",
      ]],
      ["Not a medical device", [
        "Afiati does not provide diagnosis, treatment or medical advice, and does not replace your doctor or pharmacist. Always follow your doctor's instructions and the medicine leaflet.",
        "Camera pulse readings are estimates, and the vital signs you log are for tracking only.",
        "You are responsible for the accuracy of the medicines, doses and times you enter.",
      ]],
      ["Emergencies", [
        "The “I need help” button alerts only the relatives who follow you. It does not call an ambulance or any official service.",
        "Alerts depend on the internet, phone settings and notification services, so we cannot guarantee they always arrive. In an emergency, call 997 directly.",
      ]],
      ["Age", [
        "You must be at least 13 years old to use the app, or the minimum age required in your country if it is higher.",
      ]],
      ["Your responsibility", [
        "Enter accurate information, and only follow someone with their knowledge and consent. Following only happens after the account owner approves it.",
        "Use what you see about your relatives only to check on them, and don't share it with others.",
        "Do not try to disrupt or break into the service, or use it in an automated way.",
      ]],
      ["Disclaimer", [
        "We provide the service as is. We work to keep it running and accurate, but we cannot guarantee it will always work without interruption or error.",
        "To the extent permitted by law, we are not responsible for any harm resulting from relying on the app instead of medical care, or from an alert that is late or does not arrive.",
      ]],
      ["Changes and termination", [
        "We may update these terms and will publish the new version on this page with the update date. Continuing to use the app after an update means you accept it.",
        "You can stop using Afiati at any time and delete your account from “Settings”, then “Delete my account and data”.",
        "We may suspend an account that violates these terms.",
        "These terms are governed by the laws of the Kingdom of Saudi Arabia.",
      ]],
    ],
  },
};

// ---------- Support ----------
const support = {
  ar: {
    title: "الدعم — عافيتي",
    description: "الدعم والأسئلة الشائعة لتطبيق عافيتي.",
    heading: "الدعم",
    intro: "أجوبة لأكثر الأسئلة تكرارًا. إذا لم تجد ما تبحث عنه فراسلنا، ونرد عادةً خلال يوم عمل.",
    contactTitle: "تواصل معنا",
    faq: [
      ["كيف أضيف دوائي؟", "اضغط زر (+) أسفل الشاشة، ثم اكتب اسم الدواء واختر الجرعة والمواعيد والتكرار، وأضف المخزون إن أردت أن نذكّرك قبل نفاده."],
      ["كيف أعدّل دواء أو أوقفه؟", "من «أدويتي» اضغط على الدواء، ثم «تعديل الدواء» أو «إيقاف». عند الإيقاف لا تصلك تذكيراته، ويبقى ما سجّلته منه في السجل."],
      ["سجّلت جرعة بالخطأ، كيف أتراجع؟", "اضغط علامة الصح المضيئة بجانب الجرعة، ثم اختر «لم آخذها». تعود الجرعة كما كانت ويرجع المخزون."],
      ["لا تصلني التذكيرات", "افتح «الإعدادات» وتأكد أن «إشعارات الأدوية» مفعّلة، وفي أندرويد فعّل «التذكير في الوقت الدقيق»، واستثنِ عافيتي من توفير البطارية. وفي الآيفون تأكد أن وضع التركيز لا يمنع إشعاراته."],
      ["كيف أوقف صوت التنبيه داخل التطبيق؟", "من «الإعدادات» أوقف «صوت تنبيه موعد الدواء». تبقى بطاقة الجرعة تومض، ويبقى الإشعار المعتاد."],
      ["كيف يتابعني ابني أو ابنتي؟", "من «العائلة» اضغط «دعوة فرد من العائلة» وأرسل الرمز. يُدخل قريبك الرمز في تطبيقه من «العائلة» ثم «أتابعهم» ثم «متابعة قريب برمز»، فيصلك طلبه لتوافق وتختار ما يراه."],
      ["ماذا يحدث حين أضغط «أحتاج مساعدة»؟", "يبدأ عدّ تنازلي لثوانٍ تستطيع خلاله الإلغاء إن ضغطت بالخطأ، ثم يصل تنبيه عاجل لكل من يتابعك مع موقعك إن سمحت به، ويتكرر كل دقيقتين حتى يرد أحدهم. وحين يرد يصلك «فلان قادم إليك». وإذا صرت بخير اضغط «أنا بخير الآن»."],
      ["هل قياس النبض دقيق؟", "القراءة تقديرية وليست تشخيصًا طبيًا. للحصول على أفضل قراءة: اجلس مرتاحًا، وغطِّ الكاميرا الخلفية والفلاش بطرف إصبعك برفق دون ضغط، وابقَ ثابتًا حتى ينتهي القياس."],
      ["كيف أغيّر اللغة أو أكبّر الخط؟", "اضغط زر اللغة في أعلى الشاشة الرئيسية، أو من «الإعدادات» اختر اللغة وفعّل «خط أكبر»."],
      ["كيف أحذف حسابي؟", "من «الإعدادات» اضغط «حذف حسابي وبياناتي» ثم «حذف نهائيًا». إذا لم يعد التطبيق عندك فاطلب الحذف من صفحة <a href=\"/delete-account/\">حذف الحساب</a>."],
      ["ظهرت «الخدمة متوقفة مؤقتًا» أو «يتوفر تحديث»", "الأولى تعني أننا أوقفنا الخدمة مؤقتًا للصيانة، وتذكيرات أدويتك تبقى تصلك، وسيعود التطبيق تلقائيًا. الثانية تعني أن نسختك قديمة، فاضغط «تحديث التطبيق»."],
    ],
  },
  en: {
    title: "Support — Afiati",
    description: "Support and frequently asked questions for Afiati.",
    heading: "Support",
    intro: "Answers to the most common questions. If you can't find what you need, email us. We usually reply within one business day.",
    contactTitle: "Contact us",
    faq: [
      ["How do I add a medicine?", "Tap the (+) button at the bottom, type the medicine name, then choose the dose, times and repeat. Add the stock if you'd like a reminder before it runs out."],
      ["How do I edit or stop a medicine?", "In “My medicines”, tap the medicine, then “Edit medicine” or “Stop”. When stopped, its reminders end and what you logged stays in your history."],
      ["I marked a dose by mistake. How do I undo it?", "Tap the lit check mark next to the dose, then choose “I didn't take it”. The dose goes back to pending and the stock is restored."],
      ["I'm not getting reminders", "Open “Settings” and make sure “Medicine notifications” is on. On Android, turn on “Exact-time reminders” and exclude Afiati from battery saving. On iPhone, make sure no Focus mode is silencing it."],
      ["How do I turn off the in-app alert sound?", "In “Settings”, turn off “Medicine time alert sound”. The dose card still flashes, and the usual notification still arrives."],
      ["How can my son or daughter follow me?", "In “Family”, tap “Invite a family member” and send the code. Your relative enters it in their app under “Family”, “I follow”, then “Follow a relative by code”. You then get their request to approve and choose what they see."],
      ["What happens when I tap “I need help”?", "A short countdown starts so you can cancel if you pressed by mistake. Then everyone who follows you gets an urgent alert with your location if you allowed it, repeated every 2 minutes until someone answers. When they do, you see “… is coming to you”. When you're fine, tap “I'm okay now”."],
      ["Is the pulse measurement accurate?", "It's an estimate, not a medical diagnosis. For the best reading, sit comfortably, cover the back camera and flash gently with your fingertip without pressing, and keep still until it finishes."],
      ["How do I change the language or make text larger?", "Tap the language button at the top of the home screen, or open “Settings” to choose the language and turn on “Larger text”."],
      ["How do I delete my account?", "In “Settings”, tap “Delete my account and data”, then “Delete for good”. If you no longer have the app, request deletion on the <a href=\"/en/delete-account/\">Delete account</a> page."],
      ["I see “The service is paused” or “An update is available”", "The first means we paused the service for maintenance. Your medicine reminders still arrive, and the app comes back by itself. The second means your version is out of date, so tap “Update the app”."],
    ],
  },
};

// ---------- Account deletion (linked from Google Play) ----------
const deletion = {
  ar: {
    title: "حذف الحساب — عافيتي",
    description: "كيف تحذف حسابك في عافيتي وكل بياناتك.",
    heading: "حذف حسابك في عافيتي",
    intro: "تستطيع حذف حسابك وكل بياناتك نهائيًا في أي وقت.",
    sections: [
      ["من داخل التطبيق (الأسرع)", [
        "افتح عافيتي واضغط علامة الترس لفتح «الإعدادات».",
        "انزل إلى «الحساب» واضغط «حذف حسابي وبياناتي».",
        "اضغط «حذف نهائيًا». يتم الحذف فورًا، ويعود التطبيق إلى البداية. يحتاج ذلك اتصالًا بالإنترنت.",
      ]],
      ["إذا لم يعد التطبيق عندك", [
        `أرسل طلب الحذف إلى ${mail} من بريدك، واكتب فيه اسمك ورقم الجوال المسجّل في التطبيق.`,
        "لأن عافيتي لا يستخدم كلمة مرور، قد نطلب تأكيدًا إضافيًا أنك صاحب الحساب قبل الحذف. ننفّذ الطلب خلال 30 يومًا على الأكثر، ونؤكد لك بالبريد.",
      ]],
      ["ما الذي يُحذف", [
        "من الخادم: ملفك (الاسم والجوال والبريد)، وأجهزتك ورموز الإشعارات، وأدويتك وسجل جرعاتك، وطلبات المساعدة ومواقعها، والإشعارات، وروابط العائلة في الاتجاهين، وسجل نشاط الجهاز اليومي.",
        "من جوالك (عند الحذف من التطبيق): كل ما سبق، وقراءات النبض والعلامات الحيوية ورقم الطوارئ.",
      ]],
      ["ما الذي يبقى", [
        "لا نحتفظ بشيء من بياناتك بعد الحذف.",
        "الرسائل التي وصلت سابقًا إلى جوالات أقاربك تبقى على جوالاتهم.",
        "بصمات مشفّرة لعناوين الشبكة تُستخدم للحماية من الإساءة ولا ترتبط بحسابك، وتُحذف تلقائيًا خلال يومين، وسجلات التشغيل التقنية في Cloudflare تُحذف تلقائيًا خلال أيام.",
      ]],
    ],
  },
  en: {
    title: "Delete account — Afiati",
    description: "How to delete your Afiati account and all your data.",
    heading: "Delete your Afiati account",
    intro: "You can permanently delete your account and all your data at any time.",
    sections: [
      ["Inside the app (fastest)", [
        "Open Afiati and tap the gear to open “Settings”.",
        "Scroll to “Account” and tap “Delete my account and data”.",
        "Tap “Delete for good”. Deletion happens right away and the app returns to the start. An internet connection is needed.",
      ]],
      ["If you no longer have the app", [
        `Email your deletion request to ${mail} from your own email address, with your name and the phone number registered in the app.`,
        "Because Afiati uses no password, we may ask for extra confirmation that the account is yours before deleting it. We complete requests within 30 days at most and confirm by email.",
      ]],
      ["What is deleted", [
        "From the server: your profile (name, phone, email), your devices and notification tokens, your medicines and dose history, help requests and their locations, notifications, family links in both directions, and the device's daily activity records.",
        "From your phone (when deleting in the app): all of the above, plus pulse readings, vital signs and the emergency number.",
      ]],
      ["What remains", [
        "We keep none of your data after deletion.",
        "Messages that already reached your relatives' phones stay on their phones.",
        "Hashed network addresses used against abuse are not linked to your account and are deleted automatically within two days, and Cloudflare's technical operating logs are deleted automatically within days.",
      ]],
    ],
  },
};

// ---------- Template ----------
function ribbon(id) {
  const lines = [];
  const w = 1440, h = 120, N = 26;
  for (let i = 0; i < N; i++) {
    const k = i / (N - 1) - 0.5;
    let d = "";
    for (let x = -10; x <= w + 10; x += 8) {
      const t = x / w;
      const center = h * 0.5 + h * 0.22 * Math.sin(t * Math.PI * 2 * 0.9 + 0.5);
      const spread = h * 0.28 * Math.sin(t * Math.PI * 2 * 0.7 + 1.1);
      d += `${x === -10 ? "M" : "L"}${x},${(center + k * spread).toFixed(1)}`;
    }
    lines.push(`<path d="${d}" stroke="url(#${id})" stroke-width="1" fill="none" opacity="${(0.35 + 0.65 * (1 - Math.abs(k) * 2)).toFixed(2)}"/>`);
  }
  return `<svg class="wave" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" aria-hidden="true"><defs><linearGradient id="${id}" x1="0" x2="1"><stop offset="0" stop-color="#2A86BC" stop-opacity=".2"/><stop offset=".5" stop-color="#4FB9F0"/><stop offset="1" stop-color="#5FE3C0" stop-opacity=".35"/></linearGradient></defs>${lines.join("")}</svg>`;
}

function layout(lang, page, content, body) {
  const c = common[lang];
  const p = paths[lang];
  const homeHref = page === "home" ? "" : p.home;
  return `<!doctype html>
<html lang="${lang}" dir="${c.dir}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="dark">
<meta name="theme-color" content="#020507">
<title>${content.title}</title>
<meta name="description" content="${content.description}">
<link rel="canonical" href="${SITE}${p[page]}">
<link rel="alternate" hreflang="ar" href="${SITE}${paths.ar[page]}">
<link rel="alternate" hreflang="en" href="${SITE}${paths.en[page]}">
<link rel="alternate" hreflang="x-default" href="${SITE}${paths.ar[page]}">
<meta property="og:type" content="website">
<meta property="og:site_name" content="${c.name}">
<meta property="og:title" content="${content.title}">
<meta property="og:description" content="${content.description}">
<meta property="og:url" content="${SITE}${p[page]}">
<meta property="og:image" content="${SITE}/assets/share.png">
<meta property="og:locale" content="${lang === "ar" ? "ar_SA" : "en_US"}">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="/assets/favicon.png" type="image/png">
<link rel="apple-touch-icon" href="/assets/apple-touch-icon.png">
<link rel="stylesheet" href="/assets/site.css">
</head>
<body class="${page}">
<a class="skip" href="#content">${c.skip}</a>
<header class="top">
  <a class="brand" href="${p.home}"><img src="/assets/icon.png" alt="" width="40" height="40"><span>${c.name}</span></a>
  <nav>
    <a class="hide-sm" href="${homeHref}#family">${c.nav.family}</a>
    <a class="hide-sm" href="${homeHref}#features">${c.nav.features}</a>
    <a href="${p.privacy}">${c.nav.privacy}</a>
    <a href="${p.support}">${c.nav.support}</a>
    <a class="lang" href="${paths[c.otherLang][page]}" lang="${c.otherLang}" hreflang="${c.otherLang}">${c.other}</a>
  </nav>
</header>
${body}
<footer>
  ${ribbon("fw")}
  <p class="by">${c.by} <a href="${DEV_SITE}"><b>${c.dev}</b></a><span>${c.role}</span></p>
  <p class="links"><a href="${p.home}">${c.name}</a> · <a href="${p.privacy}">${c.nav.privacy}</a> · <a href="${p.support}">${c.nav.support}</a> · <a href="${p.terms}">${c.nav.terms}</a> · <a href="${p.delete}">${c.nav.delete}</a> · <a href="${DEV_SITE}">alshaye.ai</a></p>
  <p class="copy">© 2026 ${c.dev}. ${c.rights}</p>
</footer>
</body>
</html>
`;
}

const phone = (lang, shot, alt, eager = false) =>
  `<img class="phone" src="/assets/shots/${shot}-${lang}.webp" alt="${alt}" width="540" height="1170"${eager ? "" : ' loading="lazy"'}>`;

function homePage(lang) {
  const t = home[lang];
  const apple = APP_STORE_URL ? `<a class="store" href="${APP_STORE_URL}">${t.appStore}</a>` : `<span class="soon">${t.soonApple} <small>${t.soon}</small></span>`;
  const play = PLAY_URL ? `<a class="store" href="${PLAY_URL}">${t.play}</a>` : `<span class="soon">${t.soonPlay} <small>${t.soon}</small></span>`;
  const body = `<main id="content">
  <section class="hero">
    <div>
      <span class="eyebrow">${t.eyebrow}</span>
      <h1>${common[lang].name}</h1>
      <p class="tagline">${t.tagline}</p>
      <p class="lead">${t.lead}</p>
      <p class="stores">${apple}${play}</p>
    </div>
    ${phone(lang, "home", t.heroAlt, true)}
  </section>
  ${ribbon("hw")}
  <section id="family">
    <h2 class="section">${t.familyTitle}</h2>
    <p class="section-lead">${t.familyLead}</p>
    <div class="family">
      ${t.family.map(([h, p, shot, alt]) => `<article><h3>${h}</h3><p>${p}</p>${phone(lang, shot, alt)}</article>`).join("\n      ")}
    </div>
    <h2 class="section">${t.stepsTitle}</h2>
    <ol class="steps">
      ${t.steps.map(([h, p]) => `<li><h3>${h}</h3><p>${p}</p></li>`).join("\n      ")}
    </ol>
  </section>
  <section id="features">
    <h2 class="section">${t.featuresTitle}</h2>
    <div class="features">
      ${t.features.map(([i, h, p]) => `<article>${icon(i)}<h3>${h}</h3><p>${p}</p></article>`).join("\n      ")}
    </div>
  </section>
  <section>
    <h2 class="section">${t.shotsTitle}</h2>
    <div class="shots">
      ${t.shots.map(([shot, cap]) => `<figure>${phone(lang, shot, cap)}<figcaption>${cap}</figcaption></figure>`).join("\n      ")}
    </div>
  </section>
  <section class="strip">
    <h2>${t.privacyTitle}</h2>
    <p>${t.privacyText}</p>
    <ul class="points">${t.points.map((x) => `<li>${x}</li>`).join("")}</ul>
    <a class="button" href="${paths[lang].privacy}">${t.privacyLink}</a>
  </section>
  <p class="note">${t.note}</p>
</main>`;
  return layout(lang, "home", t, body);
}

function docPage(lang, page, t, extra = "") {
  const c = common[lang];
  const sections = t.sections
    .map(([h, items]) => `<section><h2>${h}</h2><ul>${items.map((x) => `<li>${x}</li>`).join("")}</ul></section>`)
    .join("\n  ");
  const body = `<main id="content" class="doc">
  <h1>${t.heading}</h1>
  <p class="updated">${c.updated}: ${UPDATED[lang]}</p>
  <p class="intro">${t.intro}</p>
  ${sections}
  ${extra}
  <section><h2>${lang === "ar" ? "التواصل" : "Contact"}</h2><p>${c.contact}</p></section>
</main>`;
  return layout(lang, page, t, body);
}

function supportPage(lang) {
  const t = support[lang];
  const c = common[lang];
  const body = `<main id="content" class="doc">
  <h1>${t.heading}</h1>
  <p class="intro">${t.intro}</p>
  <section><h2>${t.contactTitle}</h2><p>${c.contact}</p></section>
  ${t.faq.map(([q, a]) => `<details><summary>${q}</summary><p>${a}</p></details>`).join("\n  ")}
</main>`;
  return layout(lang, "support", t, body);
}

function write(path, html) {
  const dir = join(out, path);
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, "index.html"), html);
}

for (const lang of ["ar", "en"]) {
  const p = paths[lang];
  write(p.home, homePage(lang));
  write(p.privacy, docPage(lang, "privacy", privacy[lang]));
  write(p.terms, docPage(lang, "terms", terms[lang]));
  write(p.delete, docPage(lang, "delete", deletion[lang]));
  write(p.support, supportPage(lang));
}
mkdirSync(join(out, "assets"), { recursive: true });
copyFileSync(join(root, "site", "site.css"), join(out, "assets", "site.css"));
writeFileSync(
  join(out, "_headers"),
  `/*
  Content-Security-Policy: default-src 'none'; style-src 'self'; img-src 'self'; font-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'
  Referrer-Policy: strict-origin-when-cross-origin
  X-Content-Type-Options: nosniff
  Permissions-Policy: camera=(), microphone=(), geolocation=()
/assets/*
  Cache-Control: public, max-age=86400
`,
);
writeFileSync(join(out, "robots.txt"), `User-agent: *\nAllow: /\nSitemap: ${SITE}/sitemap.xml\n`);
const urls = Object.values(paths).flatMap((p) => Object.values(p)).map((u) => `  <url><loc>${SITE}${u}</loc></url>`).join("\n");
writeFileSync(join(out, "sitemap.xml"), `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`);
console.log("site built in worker/public");
