--liquibase formatted sql
--changeset claude:007-feed-phrases
-- Daily phrases and expressions for mixed feed

CREATE TABLE IF NOT EXISTS feed_phrases (
  id SERIAL PRIMARY KEY,
  estonian TEXT NOT NULL,
  english TEXT NOT NULL DEFAULT '',
  turkish TEXT NOT NULL DEFAULT '',
  category TEXT NOT NULL DEFAULT 'daily',
  cefr_level TEXT NOT NULL DEFAULT 'A1',
  context_note TEXT NOT NULL DEFAULT '',
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_feed_phrases_level ON feed_phrases (cefr_level);

-- Track which phrases each user has seen
CREATE TABLE IF NOT EXISTS sent_phrases (
  chat_id BIGINT NOT NULL,
  phrase_id INTEGER NOT NULL REFERENCES feed_phrases(id) ON DELETE CASCADE,
  seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (chat_id, phrase_id)
);

-- ============================================
-- SEED: A1 Phrases
-- ============================================
INSERT INTO feed_phrases (estonian, english, turkish, category, cefr_level, context_note, sort_order) VALUES
-- Greetings
('Tere hommikust!', 'Good morning!', 'Günaydın!', 'greeting', 'A1', 'Hommikul öeldakse', 1),
('Tere õhtust!', 'Good evening!', 'İyi akşamlar!', 'greeting', 'A1', 'Õhtul öeldakse', 2),
('Head ööd!', 'Good night!', 'İyi geceler!', 'greeting', 'A1', 'Enne magama minekut', 3),
('Kuidas läheb?', 'How are you?', 'Nasılsın?', 'greeting', 'A1', 'Sõbrale küsitakse', 4),
('Hästi, aitäh!', 'Fine, thanks!', 'İyiyim, teşekkürler!', 'greeting', 'A1', 'Vastus küsimusele "Kuidas läheb?"', 5),
('Nägemiseni!', 'See you!', 'Görüşürüz!', 'greeting', 'A1', 'Hüvasti jättes', 6),
('Meeldiv tutvuda!', 'Nice to meet you!', 'Tanıştığımıza memnun oldum!', 'greeting', 'A1', 'Uue inimesega kohtudes', 7),

-- Daily expressions
('Palun!', 'Please! / You''re welcome!', 'Lütfen! / Rica ederim!', 'daily', 'A1', 'Viisakalt paludes või tänule vastates', 8),
('Vabandust!', 'Sorry! / Excuse me!', 'Özür dilerim! / Pardon!', 'daily', 'A1', 'Vabandades või tähelepanu paludes', 9),
('Pole hullu!', 'No problem! / It''s okay!', 'Sorun değil! / Önemli değil!', 'daily', 'A1', 'Kellelegi kinnitades, et kõik on korras', 10),
('Ma ei saa aru.', 'I don''t understand.', 'Anlamıyorum.', 'daily', 'A1', 'Kui midagi jääb arusaamatuks', 11),
('Palun rääkige aeglasemalt.', 'Please speak more slowly.', 'Lütfen daha yavaş konuşun.', 'daily', 'A1', 'Kui keegi räägib liiga kiiresti', 12),
('Mis kell on?', 'What time is it?', 'Saat kaç?', 'daily', 'A1', 'Aja küsimine', 13),
('Kus on tualett?', 'Where is the toilet?', 'Tuvalet nerede?', 'daily', 'A1', 'Väga vajalik küsimus!', 14),

-- Common questions
('Kui palju see maksab?', 'How much does it cost?', 'Bu ne kadar?', 'question', 'A1', 'Poes hinda küsides', 15),
('Kas te räägite inglise keelt?', 'Do you speak English?', 'İngilizce konuşuyor musunuz?', 'question', 'A1', 'Abi küsides', 16),
('Mis see on?', 'What is this?', 'Bu ne?', 'question', 'A1', 'Tundmatu asja kohta küsides', 17),

-- Reactions
('Väga hea!', 'Very good!', 'Çok iyi!', 'reaction', 'A1', 'Kiites', 18),
('Imeline!', 'Wonderful!', 'Harika!', 'reaction', 'A1', 'Midagi imelise kohta', 19),
('Kahju!', 'Too bad! / Pity!', 'Yazık! / Ne kötü!', 'reaction', 'A1', 'Halva uudise puhul', 20),

-- ============================================
-- SEED: A2 Phrases
-- ============================================
('Ma arvan, et jah.', 'I think so.', 'Öyle düşünüyorum.', 'daily', 'A2', 'Ebakindlalt nõustudes', 21),
('See on hea mõte!', 'That''s a good idea!', 'Bu iyi bir fikir!', 'reaction', 'A2', 'Kellelegi nõustudes', 22),
('Ma olen nõus.', 'I agree.', 'Katılıyorum.', 'daily', 'A2', 'Nõustumine', 23),
('Ma ei ole nõus.', 'I disagree.', 'Katılmıyorum.', 'daily', 'A2', 'Mittenõustumine', 24),
('Kas sa saaksid mind aidata?', 'Could you help me?', 'Bana yardım edebilir misin?', 'question', 'A2', 'Abi paludes', 25),
('Ma tahaksin tellida...', 'I would like to order...', 'Sipariş vermek istiyorum...', 'daily', 'A2', 'Restoranis', 26),
('Mul on hea meel!', 'I''m glad!', 'Sevindim!', 'reaction', 'A2', 'Rõõmu väljendades', 27),
('See on mulle väga tähtis.', 'This is very important to me.', 'Bu benim için çok önemli.', 'daily', 'A2', 'Tähtsust rõhutades', 28),
('Ma ootan sind!', 'I''m waiting for you!', 'Seni bekliyorum!', 'daily', 'A2', 'Kellegi ootamine', 29),
('Ära muretse!', 'Don''t worry!', 'Merak etme!', 'daily', 'A2', 'Kedagi rahustades', 30),
('Mis su lemmiktoit on?', 'What is your favorite food?', 'En sevdiğin yemek ne?', 'question', 'A2', 'Vestluses', 31),
('Ma tulen kohe tagasi.', 'I''ll be right back.', 'Hemen döneceğim.', 'daily', 'A2', 'Korraks lahkudes', 32),

-- ============================================
-- SEED: B1 Phrases
-- ============================================
('Minu meelest on see vale.', 'In my opinion, that''s wrong.', 'Bence bu yanlış.', 'daily', 'B1', 'Arvamust väljendades', 33),
('Ma ei suuda seda uskuda!', 'I can''t believe it!', 'Buna inanamıyorum!', 'reaction', 'B1', 'Üllatuse väljendamine', 34),
('Ausalt öeldes...', 'Honestly speaking...', 'Dürüst olmak gerekirse...', 'daily', 'B1', 'Avameelne arvamus', 35),
('See sõltub olukorrast.', 'It depends on the situation.', 'Duruma bağlı.', 'daily', 'B1', 'Paindlik vastus', 36),
('Mul on sinust puudus.', 'I miss you.', 'Seni özledim.', 'emotion', 'B1', 'Igatsuse väljendamine', 37),
('Kas sa oled kindel?', 'Are you sure?', 'Emin misin?', 'question', 'B1', 'Kinnituse küsimine', 38),
('Ma olen selle üle uhke!', 'I''m proud of it!', 'Bununla gurur duyuyorum!', 'emotion', 'B1', 'Uhkuse väljendamine', 39),
('Olgem ausad.', 'Let''s be honest.', 'Dürüst olalım.', 'daily', 'B1', 'Tõsist vestlust alustades', 40),
('Ma teen oma parima.', 'I''ll do my best.', 'Elimden gelenin en iyisini yapacağım.', 'daily', 'B1', 'Lubaduse andmine', 41),
('See oli uskumatu kogemus!', 'It was an incredible experience!', 'İnanılmaz bir deneyimdi!', 'reaction', 'B1', 'Kogemust kirjeldades', 42),

-- ============================================
-- SEED: B2 Phrases
-- ============================================
('Ma ei pea seda õigeks.', 'I don''t consider that right.', 'Bunu doğru bulmuyorum.', 'daily', 'B2', 'Viisakas vastuväide', 43),
('Ärme teeme kiireid järeldusi.', 'Let''s not jump to conclusions.', 'Acele sonuçlara varmayalım.', 'daily', 'B2', 'Ettevaatusele kutsudes', 44),
('See on vaieldav teema.', 'That''s a debatable topic.', 'Bu tartışmalı bir konu.', 'daily', 'B2', 'Keerulisest teemast rääkides', 45),
('Ma hindan sinu ausust.', 'I appreciate your honesty.', 'Dürüstlüğünü takdir ediyorum.', 'emotion', 'B2', 'Tänu aususe eest', 46),
('Kõik on suhteline.', 'Everything is relative.', 'Her şey göreceli.', 'daily', 'B2', 'Filosoofiline mõte', 47),
('Eesmärk pühitseb abinõu.', 'The end justifies the means.', 'Amaç aracı meşrulaştırır.', 'idiom', 'B2', 'Vanasõna — kas see on tõsi?', 48);
