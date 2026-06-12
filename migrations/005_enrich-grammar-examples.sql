-- Enrich grammar stories with more diverse example sentences

-- A1: Personal Pronouns (Zamirler) - currently 6, add 6 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Mina olen õpilane.', 'I am a student.', 'Ben bir öğrenciyim.', 3),
  ('Sina oled mu vend.', 'You are my brother.', 'Sen benim kardeşimsin.', 4),
  ('Ta on väga tark.', 'He/She is very smart.', 'O çok zeki.', 5)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-personal-pronouns' AND s.title = 'Tekil Zamirler';

INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Me räägime eesti keelt.', 'We speak Estonian.', 'Biz Estonca konuşuyoruz.', 3),
  ('Te olete väga head.', 'You are very good.', 'Siz çok iyisiniz.', 4),
  ('Nad õpivad koolis.', 'They study at school.', 'Onlar okulda öğreniyorlar.', 5)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-personal-pronouns' AND s.title = 'Çoğul Zamirler';

-- A1: Olema (Olmak) - currently 5, add 7 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Me oleme peres viis.', 'We are five in the family.', 'Ailemizde beş kişiyiz.', 3),
  ('Te olete mu sõbrad.', 'You are my friends.', 'Siz benim arkadaşlarımsınız.', 4),
  ('Nad on õnnelikud.', 'They are happy.', 'Onlar mutlular.', 5),
  ('Ma olen väsinud.', 'I am tired.', 'Ben yorgunum.', 6)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-olema-present' AND s.title = 'Geniş Zaman Çekimi';

INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ma ei ole haige.', 'I am not sick.', 'Ben hasta değilim.', 2),
  ('Sa ei ole üksi.', 'You are not alone.', 'Sen yalnız değilsin.', 3)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-olema-present' AND s.title = 'Olumsuz Form';

-- A1: Basic Word Order (Söz Dizimi) - currently 6, add 6 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ta kirjutab kirja.', 'He/She writes a letter.', 'O bir mektup yazıyor.', 3),
  ('Me ostame piima.', 'We buy milk.', 'Biz süt alıyoruz.', 4),
  ('Nad vaatavad filmi.', 'They watch a movie.', 'Onlar film izliyorlar.', 5)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-basic-word-order' AND s.title LIKE 'Düz Cümle%';

INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Kas sa armastad mind?', 'Do you love me?', 'Beni seviyor musun?', 3),
  ('Kas nad tulevad homme?', 'Are they coming tomorrow?', 'Onlar yarın geliyorlar mı?', 4),
  ('Kas me läheme?', 'Shall we go?', 'Gidelim mi?', 5)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-basic-word-order' AND s.title LIKE '%Kas%';

-- A1: Nominative Case (Yalın Hal) - currently 3, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Auto on punane.', 'The car is red.', 'Araba kırmızı.', 3),
  ('Kass magab.', 'The cat is sleeping.', 'Kedi uyuyor.', 4),
  ('Tüdruk laulab.', 'The girl is singing.', 'Kız şarkı söylüyor.', 5),
  ('Ilm on ilus.', 'The weather is beautiful.', 'Hava güzel.', 6),
  ('Poiss jookseb.', 'The boy is running.', 'Çocuk koşuyor.', 7)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-nominative-case' AND s.title LIKE '%Ne Zaman%';

-- A1: Genitive Case (Tamlayan) - currently 3, add 4 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ema auto on valge.', 'Mother''s car is white.', 'Annenin arabası beyaz.', 3),
  ('Linna nimi on Tallinn.', 'The city''s name is Tallinn.', 'Şehrin adı Tallinn.', 4),
  ('Õpetaja tuba on suur.', 'The teacher''s room is big.', 'Öğretmenin odası büyük.', 5),
  ('Sõbra koer on armas.', 'The friend''s dog is cute.', 'Arkadaşın köpeği sevimli.', 6)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-genitive-case' AND s.title LIKE '%Nasıl%';

-- A1: Negation (Olumsuzluk) - currently 3, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ma ei söö liha.', 'I don''t eat meat.', 'Ben et yemiyorum.', 10),
  ('Ta ei räägi inglise keelt.', 'He/She doesn''t speak English.', 'O İngilizce konuşmuyor.', 11),
  ('Me ei tea.', 'We don''t know.', 'Biz bilmiyoruz.', 12),
  ('Nad ei tule täna.', 'They are not coming today.', 'Onlar bugün gelmiyor.', 13),
  ('Sa ei pea kartma.', 'You don''t have to be afraid.', 'Sen korkmak zorunda değilsin.', 14)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-negation'
AND s.sort_order = (SELECT MIN(sort_order) FROM grammar_story_slides WHERE story_id = 'story-a1-negation' AND id IN (SELECT slide_id FROM grammar_slide_examples));

-- A1: Numbers (Sayılar) - currently 5, add 4 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Mul on neli õde.', 'I have four sisters.', 'Dört kız kardeşim var.', 10),
  ('Meil on seitse kassi.', 'We have seven cats.', 'Yedi kedimiz var.', 11),
  ('Viis pluss kolm on kaheksa.', 'Five plus three is eight.', 'Beş artı üç sekizdir.', 12),
  ('Kümme miinus kuus on neli.', 'Ten minus six is four.', 'On eksi altı dörttür.', 13)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-numbers'
AND s.sort_order = (SELECT MIN(sort_order) FROM grammar_story_slides WHERE story_id = 'story-a1-numbers' AND id IN (SELECT slide_id FROM grammar_slide_examples));

-- A1: Question Words (Sorular) - currently 7, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Kust sa tuled?', 'Where do you come from?', 'Nereden geliyorsun?', 10),
  ('Kuhu sa lähed?', 'Where are you going?', 'Nereye gidiyorsun?', 11),
  ('Kuidas sa end tunned?', 'How do you feel?', 'Kendini nasıl hissediyorsun?', 12),
  ('Mitu last sul on?', 'How many children do you have?', 'Kaç çocuğun var?', 13),
  ('Milline ilm on?', 'What is the weather like?', 'Hava nasıl?', 14)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-question-words'
AND s.sort_order = (SELECT MIN(sort_order) FROM grammar_story_slides WHERE story_id = 'story-a1-question-words' AND id IN (SELECT slide_id FROM grammar_slide_examples));

-- A1: Partitive Case (Kısmi Hal) - currently 5, add 4 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ma joon vett.', 'I drink water.', 'Ben su içiyorum.', 10),
  ('Ta sööb suppi.', 'He/She eats soup.', 'O çorba yiyor.', 11),
  ('Me loeme raamatut.', 'We read a book.', 'Biz kitap okuyoruz.', 12),
  ('Nad ostavad leiba.', 'They buy bread.', 'Onlar ekmek alıyorlar.', 13)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a1-partitive-case'
AND s.sort_order = (SELECT MIN(sort_order) FROM grammar_story_slides WHERE story_id = 'story-a1-partitive-case' AND id IN (SELECT slide_id FROM grammar_slide_examples));

-- A2: Past Simple (Geçmiş) - currently 5, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ma käisin eile poes.', 'I went to the store yesterday.', 'Dün markete gittim.', 10),
  ('Ta õppis ülikoolis.', 'He/She studied at university.', 'O üniversitede okudu.', 11),
  ('Me sõime restoranis.', 'We ate at a restaurant.', 'Biz restoranda yedik.', 12),
  ('Nad elasid Tartus.', 'They lived in Tartu.', 'Onlar Tartu''da yaşadılar.', 13),
  ('Sa tegid head tööd.', 'You did good work.', 'Sen iyi iş yaptın.', 14)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a2-past-simple'
AND s.sort_order = (SELECT MIN(sort_order) FROM grammar_story_slides WHERE story_id = 'story-a2-past-simple' AND id IN (SELECT slide_id FROM grammar_slide_examples));

-- A2: Comparatives (Karşılaştır) - currently 4, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Koer on suurem kui kass.', 'The dog is bigger than the cat.', 'Köpek kediden büyük.', 10),
  ('Tallinn on vanem kui Tartu.', 'Tallinn is older than Tartu.', 'Tallinn Tartu''dan eski.', 11),
  ('See tee on pikem.', 'This road is longer.', 'Bu yol daha uzun.', 12),
  ('Suvi on soojem kui kevad.', 'Summer is warmer than spring.', 'Yaz bahardan sıcak.', 13),
  ('Ma olen kiirem kui sa.', 'I am faster than you.', 'Ben senden hızlıyım.', 14)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-a2-comparatives'
AND s.sort_order = (SELECT MIN(sort_order) FROM grammar_story_slides WHERE story_id = 'story-a2-comparatives' AND id IN (SELECT slide_id FROM grammar_slide_examples));
