-- Daily greetings on the home screen. The app shows one a day at random (no repeat until all were shown).
-- To add one:     INSERT INTO greetings (text) VALUES ('...');
-- To hide one:    UPDATE greetings SET is_active = 0 WHERE id = ...;
CREATE TABLE greetings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  text TEXT NOT NULL UNIQUE CHECK (length(text) BETWEEN 2 AND 120),
  is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

INSERT INTO greetings (text) VALUES
  ('نهارك سعيد وعافيتك تدوم.'),
  ('دمت بصحة وعافية.'),
  ('أسعد الله يومك.'),
  ('يومك صحة وطمأنينة.'),
  ('صحتك أغلى ما تملك.'),
  ('عافاك الله وأدام ابتسامتك.'),
  ('كل يوم بداية جميلة.'),
  ('جعل الله أيامك عافية.'),
  ('ابتسم، فالحياة جميلة.'),
  ('صحتك تستحق اهتمامك.'),
  ('ربي يحفظك ويسعدك.'),
  ('يوم جميل بإذن الله.'),
  ('عافيتك تسعد من يحبك.'),
  ('كن بخير دائمًا.'),
  ('أدام الله عليك الصحة.'),
  ('يوم مليء بالخير والبركة.'),
  ('اعتنِ بنفسك، فأنت غالٍ.'),
  ('عسى أيامك كلها عافية.'),
  ('صحتك اليوم، سعادتك غدًا.'),
  ('حفظك الله لمن يحبك.'),
  ('ألبسك الله ثوب العافية.'),
  ('يومك سعادة وراحة بال.'),
  ('متعك الله بالصحة والعافية.'),
  ('العافية أجمل النعم.'),
  ('حفظك الله من كل مكروه.'),
  ('أدام الله عليك نعمة العافية.'),
  ('أيامك جميلة بإذن الله.'),
  ('أسعد الله قلبك.'),
  ('عافيتك تهمنا.'),
  ('طاب يومك ودامت عافيتك.');
