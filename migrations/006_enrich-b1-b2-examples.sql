-- Enrich B1 and B2 grammar stories with more diverse examples

-- B1: Conditional (Koşul) - currently 5, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ma läheksin reisile, kui mul oleks raha.', 'I would go on a trip if I had money.', 'Param olsa seyahate giderdim.', 10),
  ('Kui sa õpiksid rohkem, saaksid paremaid hindeid.', 'If you studied more, you would get better grades.', 'Daha çok çalışsan daha iyi notlar alırdın.', 11),
  ('Me ostaksime uue maja.', 'We would buy a new house.', 'Yeni bir ev alırdık.', 12),
  ('Nad elaksid Eestis, kui saaksid tööd.', 'They would live in Estonia if they got a job.', 'İş bulsalar Estonya''da yaşarlardı.', 13),
  ('Ta räägiks sinuga, kui tal oleks aega.', 'He/She would talk to you if he/she had time.', 'Zamanı olsa seninle konuşurdu.', 14)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b1-conditional'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B1: Passive (Edilgen) - currently 4, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Koolid suletakse suvel.', 'Schools are closed in summer.', 'Okullar yazın kapatılır.', 10),
  ('Seda laulu lauldakse tihti.', 'This song is often sung.', 'Bu şarkı sıkça söylenir.', 11),
  ('Eesti keelt õpitakse kogu maailmas.', 'Estonian is learned all over the world.', 'Estonca tüm dünyada öğreniliyor.', 12),
  ('Toitu valmistatakse köögis.', 'Food is prepared in the kitchen.', 'Yemek mutfakta hazırlanır.', 13),
  ('Pileteid müüakse kassas.', 'Tickets are sold at the box office.', 'Biletler gişede satılır.', 14)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b1-passive'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B1: Translative Case (Oluş Hali) - currently 3, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Vesi muutus jääks.', 'Water turned into ice.', 'Su buza dönüştü.', 10),
  ('Ta sai õpetajaks.', 'He/She became a teacher.', 'O öğretmen oldu.', 11),
  ('Ilm läks külmaks.', 'The weather got cold.', 'Hava soğudu.', 12),
  ('Me valisime ta presidendiks.', 'We elected him/her president.', 'Onu başkan seçtik.', 13),
  ('Poiss kasvas suureks.', 'The boy grew up.', 'Çocuk büyüdü.', 14)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b1-translative-case'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B1: Relative Clauses (İlgi Cml) - currently 3, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Naine, kellega ma rääkisin, on arst.', 'The woman I spoke with is a doctor.', 'Konuştuğum kadın doktor.', 10),
  ('Linn, kus ma elan, on väike.', 'The city where I live is small.', 'Yaşadığım şehir küçük.', 11),
  ('Film, mida me vaatasime, oli hea.', 'The movie we watched was good.', 'İzlediğimiz film iyiydi.', 12),
  ('Maja, kus ta sündis, on vana.', 'The house where he/she was born is old.', 'Doğduğu ev eski.', 13),
  ('Inimene, keda sa otsid, on siin.', 'The person you are looking for is here.', 'Aradığın kişi burada.', 14)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b1-relative-clauses'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B1: Reported Speech (Aktarım) - currently 3, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ta ütles, et tuleb homme.', 'He/She said that he/she would come tomorrow.', 'Yarın geleceğini söyledi.', 10),
  ('Nad arvasid, et me oleme kodus.', 'They thought we were at home.', 'Evde olduğumuzu düşündüler.', 11),
  ('Õpetaja küsis, kas me oleme valmis.', 'The teacher asked if we were ready.', 'Öğretmen hazır olup olmadığımızı sordu.', 12),
  ('Ma kuulsin, et pood olevat suletud.', 'I heard the store was supposedly closed.', 'Mağazanın kapandığını duydum.', 13),
  ('Leht kirjutas, et suvi tulevat soe.', 'The newspaper wrote that summer would be warm.', 'Gazete yazın sıcak olacağını yazdı.', 14)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b1-reported-speech'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B1: Verb Rection (Fiil Hali) - currently 6, add 4 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ma mõtlen sinu peale.', 'I think about you.', 'Seni düşünüyorum.', 10),
  ('Ta naerab nalja üle.', 'He/She laughs at the joke.', 'O şakaya gülüyor.', 11),
  ('Me sõltume ilmast.', 'We depend on the weather.', 'Havaya bağlıyız.', 12),
  ('Nad osalevad võistlusel.', 'They participate in the competition.', 'Yarışmaya katılıyorlar.', 13)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b1-verb-rection'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B2: Comitative Case (Birlikte) - currently 3, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ma käin koeraga jalutamas.', 'I go for walks with my dog.', 'Köpeğimle yürüyüşe çıkarım.', 10),
  ('Ta sõitis rongiga Tartusse.', 'He/She traveled to Tartu by train.', 'Trenle Tartu''ya gitti.', 11),
  ('Me töötame koos meeskonnaga.', 'We work together with the team.', 'Takımla birlikte çalışıyoruz.', 12),
  ('Laps mängib pallliga.', 'The child plays with a ball.', 'Çocuk topla oynuyor.', 13),
  ('Sa tuled bussiga või autoga?', 'Are you coming by bus or car?', 'Otobüsle mi arabayla mı geliyorsun?', 14)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b2-comitative-case'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B2: Participles (Ortaçlar) - currently 5, add 4 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Magav laps on voodis.', 'The sleeping child is in bed.', 'Uyuyan çocuk yatakta.', 10),
  ('Katkine aken tuleb parandada.', 'The broken window needs to be fixed.', 'Kırık pencere tamir edilmeli.', 11),
  ('Loetud raamat seisab riiulil.', 'The read book stands on the shelf.', 'Okunmuş kitap rafta duruyor.', 12),
  ('Jooksev vesi on puhas.', 'Running water is clean.', 'Akan su temiz.', 13)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b2-nud-tud-participles'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B2: Imperative (Emirler) - currently 7, add 4 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Palun helista mulle!', 'Please call me!', 'Lütfen beni ara!', 10),
  ('Ärge unustage võtmeid!', 'Don''t forget the keys!', 'Anahtarları unutmayın!', 11),
  ('Lugege see raamat läbi!', 'Read this book through!', 'Bu kitabı okuyun!', 12),
  ('Ära muretse, kõik saab korda!', 'Don''t worry, everything will be fine!', 'Endişelenme, her şey düzelecek!', 13)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b2-imperative'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B2: Word Formation (Sözcük Yap) - currently 4, add 4 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Laulja laulab laval.', 'The singer sings on stage.', 'Şarkıcı sahnede şarkı söylüyor.', 10),
  ('Kirjanik kirjutab romaani.', 'The writer writes a novel.', 'Yazar roman yazıyor.', 11),
  ('Jalgpallivõistlus oli põnev.', 'The football match was exciting.', 'Futbol maçı heyecanlıydı.', 12),
  ('Linnaraamatukogu on avatud.', 'The city library is open.', 'Şehir kütüphanesi açık.', 13)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b2-word-formation'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B2: Pluperfect (Uzak Geçm) - currently 2, add 5 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ta oli juba magama läinud, kui ma helistasin.', 'He/She had already gone to sleep when I called.', 'Ben aradığımda o çoktan uyumuştu.', 10),
  ('Me olime seda filmi varem näinud.', 'We had seen this movie before.', 'Bu filmi daha önce izlemiştik.', 11),
  ('Nad olid kõik ära söönud.', 'They had eaten everything.', 'Her şeyi yemişlerdi.', 12),
  ('Ma olin unustanud tema nime.', 'I had forgotten his/her name.', 'Onun adını unutmuştum.', 13),
  ('Rong oli juba lahkunud, kui me jõudsime.', 'The train had already left when we arrived.', 'Biz vardığımızda tren çoktan kalkmıştı.', 14)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b2-pluperfect'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B2: Clause Connectors (Bağlaçlar) - currently 6, add 4 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Kuigi sajab vihma, lähme välja.', 'Although it''s raining, we''ll go out.', 'Yağmur yağmasına rağmen dışarı çıkacağız.', 10),
  ('Ma õpin, et saada head tööd.', 'I study in order to get a good job.', 'İyi bir iş bulmak için okuyorum.', 11),
  ('Niipea kui ta tuleb, alustame.', 'As soon as he/she comes, we''ll start.', 'O gelir gelmez başlayacağız.', 12),
  ('Mida rohkem õpid, seda targemaks saad.', 'The more you study, the smarter you get.', 'Ne kadar çok çalışırsan o kadar akıllı olursun.', 13)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b2-clause-connectors'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);

-- B2: Object Rules (Nesneler) - currently 7, add 3 more
INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT s.id, v.estonian, v.english, v.turkish, v.sort_order
FROM grammar_story_slides s
CROSS JOIN (VALUES
  ('Ma lugesin raamatu läbi.', 'I read the book through.', 'Kitabı baştan sona okudum.', 10),
  ('Ta ostis uue auto.', 'He/She bought a new car.', 'Yeni bir araba aldı.', 11),
  ('Nad ehitavad maja.', 'They are building a house.', 'Bir ev inşa ediyorlar.', 12)
) AS v(estonian, english, turkish, sort_order)
WHERE s.story_id = 'story-b2-object-rules'
AND s.id IN (SELECT slide_id FROM grammar_slide_examples);
