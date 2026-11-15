--liquibase formatted sql

--changeset quiz-bank-round3:1 splitStatements:false
-- 'kurss' was the study-course word (tr kurs) but every sentence uses 'kursus': name it so. Learners' lists
-- and quiz history store the word's text, so they follow the rename.
UPDATE words SET estonian = 'kursus' WHERE id = 'a2-kurss' AND estonian = 'kurss'
  AND NOT EXISTS (SELECT 1 FROM words WHERE estonian = 'kursus');

--changeset quiz-bank-round3:2 splitStatements:false
UPDATE sent_words SET word_value = 'kursus' WHERE word_id = 'a2-kurss' AND word_value = 'kurss';

--changeset quiz-bank-round3:3 splitStatements:false
UPDATE quiz_answers SET estonian = 'kursus' WHERE estonian = 'kurss'
  AND EXISTS (SELECT 1 FROM words WHERE id = 'a2-kurss' AND estonian = 'kursus');

--changeset quiz-bank-round3:4 splitStatements:false
INSERT INTO word_quiz_items (word_id, sentence_id, answer, morph_code, form_options, word_options)
SELECT 'a2-kurss', s.id, 'kursusel', 'SgAd', ARRAY['kursusele','kursuselt','kursuses']::text[], '{}'::text[]
FROM word_sentences s WHERE s.word_id = 'a2-kurss' AND s.estonian = 'Ma käin inglise keele kursusel.'
ON CONFLICT (sentence_id, answer) DO NOTHING;

--changeset quiz-bank-round3:5 splitStatements:false
-- armukade's sentence was about envy (kade): replace it with romantic jealousy
UPDATE word_sentences SET estonian = 'Ta muutus armukadedaks, kui nägi oma pruuti teisega tantsimas.',
    turkish = 'Nişanlısını başka biriyle dans ederken görünce kıskançlaştı.',
    english = 'He became jealous when he saw his fiancée dancing with someone else.'
WHERE word_id = 'b1-armukade' AND estonian = 'Ära ole armukade teiste edu peale.';

--changeset quiz-bank-round3:6 splitStatements:false
INSERT INTO word_quiz_items (word_id, sentence_id, answer, morph_code, form_options, word_options)
SELECT 'b1-armukade', s.id, 'armukadedaks', 'SgTr', ARRAY['armukadeda','armukadedas','armukadedana']::text[], '{}'::text[]
FROM word_sentences s WHERE s.word_id = 'b1-armukade' AND s.estonian = 'Ta muutus armukadedaks, kui nägi oma pruuti teisega tantsimas.'
ON CONFLICT (sentence_id, answer) DO NOTHING;

--changeset quiz-bank-round3:7 splitStatements:false
-- Words stored in the plural (kindad, saapad, käed…) have no Ekilex entry of their own: sentences and gap
-- items built on their singular's forms (kinnas, saabas, käsi…)
INSERT INTO word_sentences (word_id, estonian, english, turkish, sort_order)
SELECT v.word_id, v.et, v.en, v.tr, 200 FROM (VALUES
    ('a2-kindad', 'Ma ei leia oma kindaid.', 'I can''t find my gloves.', 'Eldivenlerimi bulamıyorum.'),
    ('a1-õlad', 'Ta pani käe mu õlale.', 'He put his hand on my shoulder.', 'Elini omzuma koydu.'),
    ('a1-silmad', 'Mul on midagi silmas.', 'There is something in my eye.', 'Gözümde bir şey var.'),
    ('a1-tossud', 'Ma ei kanna kunagi tosse.', 'I never wear sneakers.', 'Hiç spor ayakkabı giymem.'),
    ('a2-sokid', 'Ma leidsin voodi alt kaks sokki.', 'I found two socks under the bed.', 'Yatağın altında iki çorap buldum.'),
    ('a1-kõrvad', 'Ta sosistas mulle midagi kõrva.', 'He whispered something in my ear.', 'Kulağıma bir şey fısıldadı.'),
    ('b1-maksud', 'Paljud inimesed kurdavad kõrgete maksude üle.', 'Many people complain about high taxes.', 'Birçok insan yüksek vergilerden şikâyet ediyor.'),
    ('a1-sõrmed', 'Ta lõikas noaga sõrme.', 'He cut his finger with a knife.', 'Bıçakla parmağını kesti.'),
    ('a1-varbad', 'Ma lõin varba vastu lauda.', 'I stubbed my toe on the table.', 'Ayak parmağımı masaya çarptım.'),
    ('a1-saapad', 'Pori jäi mu saabaste külge.', 'Mud stuck to my boots.', 'Çamur çizmelerime yapıştı.'),
    ('a1-sandaalid', 'Talvel ei kanna keegi sandaale.', 'Nobody wears sandals in winter.', 'Kışın kimse sandalet giymez.'),
    ('a1-käed', 'Võta raamat kätte.', 'Take the book in your hand.', 'Kitabı eline al.'),
    ('a1-jalad', 'Pane sokid jalga!', 'Put your socks on!', 'Çoraplarını giy!'),
    ('b1-uudised', 'Ma kuulsin seda uudistest.', 'I heard it on the news.', 'Bunu haberlerden duydum.')
) AS v(word_id, et, en, tr)
WHERE EXISTS (SELECT 1 FROM words w WHERE w.id = v.word_id)
ON CONFLICT (word_id, estonian) DO NOTHING;

--changeset quiz-bank-round3:8 splitStatements:false
INSERT INTO word_quiz_items (word_id, sentence_id, answer, morph_code, form_options, word_options)
SELECT v.word_id, s.id, v.answer, v.code, v.forms, '{}'::text[] FROM (VALUES
    ('a2-kindad', 'Ma ei leia oma kindaid.', 'kindaid', 'PlP', ARRAY['kindad','kinnastes','kinnastega']::text[]),
    ('a1-õlad', 'Ta pani käe mu õlale.', 'õlale', 'SgAll', ARRAY['õlasse','õlast','õlal']::text[]),
    ('a1-silmad', 'Mul on midagi silmas.', 'silmas', 'SgIn', ARRAY['silmast','silmale','silmasse']::text[]),
    ('a1-tossud', 'Ma ei kanna kunagi tosse.', 'tosse', 'PlP', ARRAY['tossud','tossudes','tossudega']::text[]),
    ('a2-sokid', 'Ma leidsin voodi alt kaks sokki.', 'sokki', 'SgP', ARRAY['sokk','sokid','sokke']::text[]),
    ('a1-kõrvad', 'Ta sosistas mulle midagi kõrva.', 'kõrva', 'SgAdt', ARRAY['kõrvas','kõrvast','kõrvale']::text[]),
    ('b1-maksud', 'Paljud inimesed kurdavad kõrgete maksude üle.', 'maksude', 'PlG', ARRAY['maksud','makse','maksudega']::text[]),
    ('a1-sõrmed', 'Ta lõikas noaga sõrme.', 'sõrme', 'SgG', ARRAY['sõrmes','sõrmest','sõrmele']::text[]),
    ('a1-varbad', 'Ma lõin varba vastu lauda.', 'varba', 'SgG', ARRAY['varvas','varbasse','varbal']::text[]),
    ('a1-saapad', 'Pori jäi mu saabaste külge.', 'saabaste', 'PlG', ARRAY['saapad','saapaid','saabastes']::text[]),
    ('a1-sandaalid', 'Talvel ei kanna keegi sandaale.', 'sandaale', 'PlP', ARRAY['sandaalid','sandaalides','sandaalidega']::text[]),
    ('a1-käed', 'Võta raamat kätte.', 'kätte', 'SgAdt', ARRAY['käes','käest','käele']::text[]),
    ('a1-jalad', 'Pane sokid jalga!', 'jalga', 'SgAdt', ARRAY['jalas','jalast','jalale']::text[]),
    ('b1-uudised', 'Ma kuulsin seda uudistest.', 'uudistest', 'PlEl', ARRAY['uudised','uudiseid','uudistele']::text[])
) AS v(word_id, sentence, answer, code, forms)
JOIN word_sentences s ON s.word_id = v.word_id AND s.estonian = v.sentence
ON CONFLICT (sentence_id, answer) DO NOTHING;
