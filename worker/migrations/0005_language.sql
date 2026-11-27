-- Arabic / English interface.
-- devices.language: the interface language the phone reports (X-Language header). Family messages are
-- written in the language of the reader's most recent device; NULL (older builds) means Arabic.
ALTER TABLE devices ADD COLUMN language TEXT CHECK (language IN ('ar', 'en'));

-- English greeting next to each Arabic one. A greeting without English is not shown in the English interface.
ALTER TABLE greetings ADD COLUMN text_en TEXT;

UPDATE greetings SET text_en = CASE text
  WHEN 'نهارك سعيد وعافيتك تدوم.' THEN 'Have a lovely day, and stay well.'
  WHEN 'دمت بصحة وعافية.' THEN 'Wishing you health and wellness.'
  WHEN 'أسعد الله يومك.' THEN 'May your day be happy.'
  WHEN 'يومك صحة وطمأنينة.' THEN 'A day of health and peace of mind.'
  WHEN 'صحتك أغلى ما تملك.' THEN 'Your health is your greatest treasure.'
  WHEN 'عافاك الله وأدام ابتسامتك.' THEN 'May you stay well and keep smiling.'
  WHEN 'كل يوم بداية جميلة.' THEN 'Every day is a beautiful beginning.'
  WHEN 'جعل الله أيامك عافية.' THEN 'May all your days be healthy.'
  WHEN 'ابتسم، فالحياة جميلة.' THEN 'Smile, life is beautiful.'
  WHEN 'صحتك تستحق اهتمامك.' THEN 'Your health deserves your care.'
  WHEN 'ربي يحفظك ويسعدك.' THEN 'May God keep you happy and safe.'
  WHEN 'يوم جميل بإذن الله.' THEN 'A beautiful day, God willing.'
  WHEN 'عافيتك تسعد من يحبك.' THEN 'Your wellness brings joy to those who love you.'
  WHEN 'كن بخير دائمًا.' THEN 'Stay well, always.'
  WHEN 'أدام الله عليك الصحة.' THEN 'May God keep you healthy.'
  WHEN 'يوم مليء بالخير والبركة.' THEN 'A day full of goodness and blessings.'
  WHEN 'اعتنِ بنفسك، فأنت غالٍ.' THEN 'Take care of yourself, you are precious.'
  WHEN 'عسى أيامك كلها عافية.' THEN 'May all your days be well.'
  WHEN 'صحتك اليوم، سعادتك غدًا.' THEN 'Your health today is your happiness tomorrow.'
  WHEN 'حفظك الله لمن يحبك.' THEN 'May God protect you for those who love you.'
  WHEN 'ألبسك الله ثوب العافية.' THEN 'May God dress you in good health.'
  WHEN 'يومك سعادة وراحة بال.' THEN 'A day of happiness and peace of mind.'
  WHEN 'متعك الله بالصحة والعافية.' THEN 'May God grant you health and wellness.'
  WHEN 'العافية أجمل النعم.' THEN 'Wellness is the finest blessing.'
  WHEN 'حفظك الله من كل مكروه.' THEN 'May God keep you safe from all harm.'
  WHEN 'أدام الله عليك نعمة العافية.' THEN 'May God keep you in good health.'
  WHEN 'أيامك جميلة بإذن الله.' THEN 'Your days are beautiful, God willing.'
  WHEN 'أسعد الله قلبك.' THEN 'May God fill your heart with joy.'
  WHEN 'عافيتك تهمنا.' THEN 'Your wellness matters to us.'
  WHEN 'طاب يومك ودامت عافيتك.' THEN 'Have a good day, and stay well.'
  ELSE text_en
END;
