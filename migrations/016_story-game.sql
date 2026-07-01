-- Story-based adventure game for language learning

CREATE TABLE IF NOT EXISTS adventure_stories (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  subtitle_tr TEXT NOT NULL DEFAULT '',
  subtitle_en TEXT NOT NULL DEFAULT '',
  genre_tr TEXT NOT NULL DEFAULT '',
  genre_en TEXT NOT NULL DEFAULT '',
  emoji TEXT NOT NULL DEFAULT '',
  color TEXT NOT NULL DEFAULT '#5A7A6A',
  cefr_level TEXT NOT NULL DEFAULT 'A1',
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS story_nodes (
  id TEXT NOT NULL,
  story_id TEXT NOT NULL REFERENCES adventure_stories(id) ON DELETE CASCADE,
  stage INTEGER NOT NULL DEFAULT 1,
  language_level TEXT NOT NULL DEFAULT 'A1',
  speaker TEXT NOT NULL DEFAULT 'Narrator',
  scene TEXT NOT NULL DEFAULT 'forest_day',
  text_ee TEXT NOT NULL,
  text_tr TEXT NOT NULL DEFAULT '',
  sort_order INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (story_id, id)
);

CREATE INDEX IF NOT EXISTS idx_story_nodes_story ON story_nodes (story_id, sort_order);

CREATE TABLE IF NOT EXISTS story_node_vocabulary (
  id SERIAL PRIMARY KEY,
  story_id TEXT NOT NULL,
  node_id TEXT NOT NULL,
  word TEXT NOT NULL,
  translation TEXT NOT NULL,
  context_hint TEXT NOT NULL DEFAULT '',
  sort_order INTEGER NOT NULL DEFAULT 0,
  word_id TEXT REFERENCES words(id) ON DELETE SET NULL,
  FOREIGN KEY (story_id, node_id) REFERENCES story_nodes(story_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_story_vocab_node ON story_node_vocabulary (story_id, node_id, sort_order);

CREATE TABLE IF NOT EXISTS story_node_choices (
  id TEXT NOT NULL,
  story_id TEXT NOT NULL,
  node_id TEXT NOT NULL,
  text_ee TEXT NOT NULL,
  text_tr TEXT NOT NULL DEFAULT '',
  is_correct_grammar BOOLEAN NOT NULL DEFAULT true,
  feedback_ee TEXT NOT NULL DEFAULT '',
  feedback_tr TEXT NOT NULL DEFAULT '',
  next_node_id TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (story_id, node_id, id),
  FOREIGN KEY (story_id, node_id) REFERENCES story_nodes(story_id, id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS story_node_minigame (
  story_id TEXT NOT NULL,
  node_id TEXT NOT NULL,
  game_type TEXT NOT NULL DEFAULT 'MemoryGame',
  target_words TEXT[] NOT NULL DEFAULT '{}',
  completion_node_id TEXT NOT NULL,
  PRIMARY KEY (story_id, node_id),
  FOREIGN KEY (story_id, node_id) REFERENCES story_nodes(story_id, id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS story_progress (
  chat_id BIGINT NOT NULL,
  story_id TEXT NOT NULL REFERENCES adventure_stories(id) ON DELETE CASCADE,
  current_node_id TEXT NOT NULL,
  words_learned INTEGER NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed BOOLEAN NOT NULL DEFAULT false,
  completed_at TIMESTAMPTZ,
  PRIMARY KEY (chat_id, story_id)
);

CREATE INDEX IF NOT EXISTS idx_story_progress_user ON story_progress (chat_id);

-- ============================================
-- SEED: Story 1 — Keelemaailm (Fantasy)
-- ============================================
INSERT INTO adventure_stories (id, title, subtitle_tr, subtitle_en, genre_tr, genre_en, emoji, color, cefr_level, sort_order)
VALUES ('keelemaailm', 'Keelemaailm', 'Mati ile kelimelerin dünyasını keşfet', 'Explore the world of words with Mati', 'Fantastik Macera', 'Fantasy Adventure', '🏔️', '#5A7A6A', 'A1', 1);

INSERT INTO story_nodes (id, story_id, stage, language_level, speaker, scene, text_ee, text_tr, sort_order) VALUES
('st1_wake_up', 'keelemaailm', 1, 'A1', 'Anlatici', 'dark_awakening', 'Sa avad silmad. Kus sa oled? Siin on pime ja vaikne. Kuid kaugel paistab valgus.', 'Gözlerini açıyorsun. Neredesin? Burası karanlık ve sessiz. Ama uzakta bir ışık parlıyor.', 1),
('st1_reach_light', 'keelemaailm', 1, 'A1', 'Anlatici', 'sunrise_clearing', 'Sa jõuad lagendikule. Päike tõuseb. Taevas on ilus ja sinine. Sa näed metsa ja jõge.', 'Bir açıklığa ulaşıyorsun. Güneş yükseliyor. Gökyüzü güzel ve mavi. Bir orman ve nehir görüyorsun.', 2),
('st1_meet_mati', 'keelemaailm', 1, 'A1', 'Mati', 'forest_day', 'Tere! Minu nimi on Mati. Kes sina oled? Sa ei ole siit, eks?', 'Merhaba! Benim adım Mati. Sen kimsin? Buralardan değilsin, değil mi?', 3),
('st1_mati_explains', 'keelemaailm', 1, 'A1', 'Mati', 'forest_day', 'See on Keelemaailm — sõnade maailm! Siin sa õpid uusi sõnu. Mina olen sinu sõber ja abiline.', 'Burası Keelemaailm — kelimelerin dünyası! Burada yeni kelimeler öğreneceksin. Ben senin arkadaşın ve yardımcınım.', 4),
('st1_first_lesson', 'keelemaailm', 1, 'A1', 'Mati', 'forest_day', 'Vaata ringi! Mis sa näed? Seal on puu, seal on kivi, ja seal on lill.', 'Etrafına bak! Ne görüyorsun? Orada bir ağaç, orada bir taş ve orada bir çiçek var.', 5),
('st1_colors', 'keelemaailm', 1, 'A1', 'Mati', 'forest_day', 'Värvid on tähtsad! Taevas on sinine. Rohi on roheline. Päike on kollane. Ja see lill on punane.', 'Renkler önemli! Gökyüzü mavi. Çimen yeşil. Güneş sarı. Ve bu çiçek kırmızı.', 6),
('st1_numbers', 'keelemaailm', 1, 'A1', 'Mati', 'forest_day', 'Loendame koos! Üks puu, kaks kivi, kolm lille, neli lindu, viis pilve.', 'Birlikte sayalım! Bir ağaç, iki taş, üç çiçek, dört kuş, beş bulut.', 7),
('st1_memory_gate', 'keelemaailm', 1, 'A1', 'Mati', 'forest_day', 'Enne edasi minekut — proovime su mälu! Kas sa mäletad sõnu, mida me õppisime?', 'İlerlemeden önce — hafızanı deneyelim! Öğrendiğimiz kelimeleri hatırlıyor musun?', 8),
('st1_after_game', 'keelemaailm', 1, 'A1', 'Mati', 'forest_day', 'Suurepärane! Sa mäletad kõike! Nüüd lähme jõe äärde. Ma olen janu.', 'Harika! Her şeyi hatırlıyorsun! Şimdi nehre gidelim. Susadım.', 9),
('st1_river', 'keelemaailm', 1, 'A1', 'Anlatici', 'river', 'Te jõuate jõe äärde. Vesi on selge ja külm. Kalad ujuvad vees. Lind laulab puus.', 'Nehre ulaşıyorsunuz. Su berrak ve soğuk. Balıklar suda yüzüyor. Kuş ağaçta şarkı söylüyor.', 10),
('st1_river_choice', 'keelemaailm', 1, 'A1', 'Mati', 'bridge', 'Me peame jõest üle saama. Seal on kaks teed: üks sild ja üks kitsas rada.', 'Nehrin karşısına geçmeliyiz. İki yol var: bir köprü ve bir dar patika.', 11),
('st1_cross_bridge', 'keelemaailm', 1, 'A1', 'Anlatici', 'forest_day', 'Te lähete üle jõe. Teisel pool on suur lagendik. Kaugel paistab vana maja.', 'Nehrin karşısına geçiyorsunuz. Diğer tarafta büyük bir açıklık var. Uzakta eski bir ev görünüyor.', 12),
('st1_old_house', 'keelemaailm', 1, 'A1', 'Mati', 'house_exterior', 'See maja on väga vana. Uks on lahti. Sees on laud, tool ja raamat. Kas me läheme sisse?', 'Bu ev çok eski. Kapı açık. İçeride bir masa, sandalye ve kitap var. İçeri girelim mi?', 13),
('st1_book', 'keelemaailm', 1, 'A1', 'Mati', 'house_interior', 'Vaata seda raamatut! Siin on kaart. See näitab teed linna juurde. Linn on kaugel, aga me saame hakkama!', 'Şu kitaba bak! İçinde bir harita var. Şehre giden yolu gösteriyor. Şehir uzakta ama başarabiliriz!', 14),
('st1_memory_gate_2', 'keelemaailm', 1, 'A1', 'Mati', 'house_interior', 'Aga enne seda — kas sa mäletad kõiki uusi sõnu? Proovi veel kord!', 'Ama önce — tüm yeni kelimeleri hatırlıyor musun? Bir kez daha dene!', 15),
('st1_finale', 'keelemaailm', 1, 'A1', 'Mati', 'night_camp', 'Sa oled tõeline sõber! Koos me leiame tee linna juurde. Aga praegu puhkame. Homme algab uus päev ja uus seiklus.', 'Sen gerçek bir arkadaşsın! Birlikte şehre giden yolu bulacağız. Ama şimdi dinlenelim. Yarın yeni bir gün ve yeni bir macera başlıyor.', 16),
('st1_complete', 'keelemaailm', 1, 'A1', 'Anlatici', 'sunrise_clearing', '1. etapp on lõppenud! Sa õppisid palju uusi sõnu. Seiklus jätkub varsti...', 'Bölüm 1 tamamlandı! Çok yeni kelime öğrendin. Macera yakında devam edecek...', 17);

-- Story 1: Vocabulary
INSERT INTO story_node_vocabulary (story_id, node_id, word, translation, context_hint, sort_order) VALUES
('keelemaailm', 'st1_wake_up', 'silmad', 'gözler', 'silm = göz, silmad = gözler', 1),
('keelemaailm', 'st1_wake_up', 'pime', 'karanlık', 'Etrafın karanlık olduğunda', 2),
('keelemaailm', 'st1_wake_up', 'valgus', 'ışık', 'Uzakta parlayan şey', 3),
('keelemaailm', 'st1_reach_light', 'päike', 'güneş', 'Gökyüzünde parlayan', 1),
('keelemaailm', 'st1_reach_light', 'taevas', 'gökyüzü', 'Başının üstünde', 2),
('keelemaailm', 'st1_reach_light', 'mets', 'orman', 'Ağaçlarla dolu yer', 3),
('keelemaailm', 'st1_reach_light', 'jõgi', 'nehir', 'Akan su', 4),
('keelemaailm', 'st1_meet_mati', 'tere', 'merhaba', 'Selamlama', 1),
('keelemaailm', 'st1_meet_mati', 'nimi', 'isim/ad', 'Seni tanıtan kelime', 2),
('keelemaailm', 'st1_meet_mati', 'kes', 'kim', 'Soru kelimesi - kişi', 3),
('keelemaailm', 'st1_mati_explains', 'maailm', 'dünya', 'İçinde bulunduğun yer', 1),
('keelemaailm', 'st1_mati_explains', 'sõna', 'kelime', 'Konuşurken kullandığın', 2),
('keelemaailm', 'st1_mati_explains', 'sõber', 'arkadaş', 'Mati senin...', 3),
('keelemaailm', 'st1_first_lesson', 'puu', 'ağaç', 'Yaprakları olan uzun bitki', 1),
('keelemaailm', 'st1_first_lesson', 'kivi', 'taş', 'Yerdeki sert nesne', 2),
('keelemaailm', 'st1_first_lesson', 'lill', 'çiçek', 'Renkli ve güzel bitki', 3),
('keelemaailm', 'st1_first_lesson', 'vaata', 'bak', 'Gözlerinle yap', 4),
('keelemaailm', 'st1_colors', 'sinine', 'mavi', 'Gökyüzünün rengi', 1),
('keelemaailm', 'st1_colors', 'roheline', 'yeşil', 'Çimenin rengi', 2),
('keelemaailm', 'st1_colors', 'kollane', 'sarı', 'Güneşin rengi', 3),
('keelemaailm', 'st1_colors', 'punane', 'kırmızı', 'Çiçeğin rengi', 4),
('keelemaailm', 'st1_numbers', 'üks', 'bir', '1', 1),
('keelemaailm', 'st1_numbers', 'kaks', 'iki', '2', 2),
('keelemaailm', 'st1_numbers', 'kolm', 'üç', '3', 3),
('keelemaailm', 'st1_numbers', 'neli', 'dört', '4', 4),
('keelemaailm', 'st1_numbers', 'viis', 'beş', '5', 5),
('keelemaailm', 'st1_memory_gate', 'mälu', 'hafıza', 'Hatırlama yeteneğin', 1),
('keelemaailm', 'st1_memory_gate', 'sõnu', 'kelimeleri', 'sõna kelimesinin çoğul hali', 2),
('keelemaailm', 'st1_after_game', 'janu', 'susuzluk', 'Su içmek istediğinde', 1),
('keelemaailm', 'st1_after_game', 'nüüd', 'şimdi', 'Bu anda', 2),
('keelemaailm', 'st1_river', 'vesi', 'su', 'Nehirde akan', 1),
('keelemaailm', 'st1_river', 'külm', 'soğuk', 'Suyun sıcaklığı', 2),
('keelemaailm', 'st1_river', 'kala', 'balık', 'Suda yüzen hayvan', 3),
('keelemaailm', 'st1_river', 'lind', 'kuş', 'Ağaçta öten hayvan', 4),
('keelemaailm', 'st1_river_choice', 'sild', 'köprü', 'Nehrin üzerinden geçiren yapı', 1),
('keelemaailm', 'st1_river_choice', 'rada', 'patika', 'Dar yol', 2),
('keelemaailm', 'st1_river_choice', 'tee', 'yol', 'Yürüdüğün yer', 3),
('keelemaailm', 'st1_cross_bridge', 'maja', 'ev', 'İçinde yaşanılan yapı', 1),
('keelemaailm', 'st1_cross_bridge', 'vana', 'eski', 'Uzun zaman önce yapılmış', 2),
('keelemaailm', 'st1_cross_bridge', 'suur', 'büyük', 'Küçük olmayan', 3),
('keelemaailm', 'st1_old_house', 'uks', 'kapı', 'Eve giriş', 1),
('keelemaailm', 'st1_old_house', 'laud', 'masa', 'Üzerine bir şey koyduğun mobilya', 2),
('keelemaailm', 'st1_old_house', 'tool', 'sandalye', 'Oturduğun mobilya', 3),
('keelemaailm', 'st1_old_house', 'raamat', 'kitap', 'Okuduğun şey', 4),
('keelemaailm', 'st1_book', 'kaart', 'harita', 'Yolları gösteren çizim', 1),
('keelemaailm', 'st1_book', 'linn', 'şehir', 'Çok insanın yaşadığı yer', 2),
('keelemaailm', 'st1_book', 'kaugel', 'uzakta', 'Yakın olmayan', 3),
('keelemaailm', 'st1_finale', 'homme', 'yarın', 'Bugünden sonraki gün', 1),
('keelemaailm', 'st1_finale', 'praegu', 'şimdi', 'Bu anda', 2),
('keelemaailm', 'st1_finale', 'seiklus', 'macera', 'Heyecanlı yolculuk', 3);

-- Story 1: Choices
INSERT INTO story_node_choices (id, story_id, node_id, text_ee, text_tr, is_correct_grammar, feedback_ee, feedback_tr, next_node_id, sort_order) VALUES
('st1_wake_go_light', 'keelemaailm', 'st1_wake_up', 'Mina lähen valguse poole.', 'Işığa doğru gidiyorum.', true, 'Hea valik! Sa liigud valguse poole.', 'İyi seçim! Işığa doğru ilerliyorsun.', 'st1_reach_light', 1),
('st1_wake_stay', 'keelemaailm', 'st1_wake_up', 'Mina jään siia.', 'Burada kalıyorum.', true, 'Sa ootad natuke... Aga midagi ei juhtu. Parem on edasi minna.', 'Biraz bekliyorsun... Ama bir şey olmuyor. İlerlemek daha iyi.', 'st1_reach_light', 2),
('st1_light_look', 'keelemaailm', 'st1_reach_light', 'See on ilus!', 'Bu güzel!', true, 'Jah, see maailm on ilus.', 'Evet, bu dünya güzel.', 'st1_meet_mati', 1),
('st1_greet_mati', 'keelemaailm', 'st1_meet_mati', 'Tere, Mati! Mina olen...', 'Merhaba, Mati! Ben...', true, 'Mati naeratab. "Tore tutvuda!"', 'Mati gülümsüyor. "Tanıştığımıza memnun oldum!"', 'st1_mati_explains', 1),
('st1_confused', 'keelemaailm', 'st1_meet_mati', 'Kus mina olen?', 'Ben neredeyim?', true, 'Mati vaatab sind. "Hea küsimus! Ma seletan."', 'Mati sana bakıyor. "İyi soru! Açıklayayım."', 'st1_mati_explains', 2),
('st1_ready', 'keelemaailm', 'st1_mati_explains', 'Ma olen valmis!', 'Hazırım!', true, 'Suurepärane! Lähme!', 'Harika! Gidelim!', 'st1_first_lesson', 1),
('st1_see_tree', 'keelemaailm', 'st1_first_lesson', 'Mina näen suurt puud!', 'Büyük bir ağaç görüyorum!', true, 'Jah! See on suur tamm.', 'Evet! Bu büyük bir meşe ağacı.', 'st1_colors', 1),
('st1_see_flower', 'keelemaailm', 'st1_first_lesson', 'See lill on ilus!', 'Bu çiçek güzel!', true, 'Tõesti! See on punane lill.', 'Gerçekten! Bu kırmızı bir çiçek.', 'st1_colors', 2),
('st1_colors_got_it', 'keelemaailm', 'st1_colors', 'Ma saan aru!', 'Anlıyorum!', true, 'Väga hea!', 'Çok iyi!', 'st1_numbers', 1),
('st1_count_ok', 'keelemaailm', 'st1_numbers', 'Üks, kaks, kolm, neli, viis!', 'Bir, iki, üç, dört, beş!', true, 'Perfektne! Sa oled kiire õppija!', 'Mükemmel! Hızlı öğreniyorsun!', 'st1_memory_gate', 1),
('st1_go_river', 'keelemaailm', 'st1_after_game', 'Jah, lähme!', 'Evet, gidelim!', true, 'Sa ja Mati kõnnite jõe poole.', 'Sen ve Mati nehre doğru yürüyorsunuz.', 'st1_river', 1),
('st1_drink', 'keelemaailm', 'st1_river', 'Ma joon vett.', 'Su içiyorum.', true, 'Vesi on hea ja värske!', 'Su güzel ve taze!', 'st1_river_choice', 1),
('st1_watch_fish', 'keelemaailm', 'st1_river', 'Ma vaatan kalu.', 'Balıkları izliyorum.', true, 'Kalad on ilusad — hõbedased ja kiired!', 'Balıklar güzel — gümüşi ve hızlı!', 'st1_river_choice', 2),
('st1_bridge', 'keelemaailm', 'st1_river_choice', 'Lähme üle silla!', 'Köprüden geçelim!', true, 'Hea mõte! Sild on turvaline.', 'İyi fikir! Köprü güvenli.', 'st1_cross_bridge', 1),
('st1_path', 'keelemaailm', 'st1_river_choice', 'Mina lähen rada.', 'Patikadan gidiyorum.', false, 'Peaaegu! Õige oleks: "Mina lähen mööda rada." Aga mõttest saadi aru!', 'Neredeyse! Doğrusu: "Mina lähen mööda rada." Ama anlaşıldı!', 'st1_cross_bridge', 2),
('st1_go_house', 'keelemaailm', 'st1_cross_bridge', 'Lähme maja juurde!', 'Eve gidelim!', true, 'Mati noogutab. "See on hea plaan."', 'Mati başını sallıyor. "Bu iyi bir plan."', 'st1_old_house', 1),
('st1_enter', 'keelemaailm', 'st1_old_house', 'Jah, lähme sisse!', 'Evet, girelim!', true, 'Te astute vanasse majja.', 'Eski eve giriyorsunuz.', 'st1_book', 1),
('st1_careful', 'keelemaailm', 'st1_old_house', 'Ole ettevaatlik!', 'Dikkatli ol!', true, 'Mati noogutab. "Jah, oleme ettevaatlikud."', 'Mati başını sallıyor. "Evet, dikkatli olalım."', 'st1_book', 2),
('st1_adventure', 'keelemaailm', 'st1_book', 'Seiklus algab!', 'Macera başlıyor!', true, 'Mati naeratab laialdaselt. "Jah! Lähme!"', 'Mati geniş gülümsüyor. "Evet! Gidelim!"', 'st1_memory_gate_2', 1),
('st1_end', 'keelemaailm', 'st1_finale', 'Head ööd, Mati.', 'İyi geceler, Mati.', true, 'Head ööd, sõber. Näeme homme!', 'İyi geceler, arkadaşım. Yarın görüşürüz!', 'st1_complete', 1);

-- Story 1: Minigame gates
INSERT INTO story_node_minigame (story_id, node_id, game_type, target_words, completion_node_id) VALUES
('keelemaailm', 'st1_memory_gate', 'MemoryGame', ARRAY['päike','taevas','mets','puu','kivi','lill','sinine','punane','kollane','roheline'], 'st1_after_game'),
('keelemaailm', 'st1_memory_gate_2', 'MemoryGame', ARRAY['vesi','kala','lind','sild','maja','uks','laud','raamat','kaart','linn'], 'st1_finale');


-- ============================================
-- SEED: Story 2 — Tallinna Süda (Romance)
-- ============================================
INSERT INTO adventure_stories (id, title, subtitle_tr, subtitle_en, genre_tr, genre_en, emoji, color, cefr_level, sort_order)
VALUES ('tallinn_love', 'Tallinna Süda', 'Tallinn sokaklarında beklenmedik bir karşılaşma', 'An unexpected encounter in Tallinn streets', 'Romantik', 'Romance', '❤️', '#A05050', 'A1', 2);

INSERT INTO story_nodes (id, story_id, stage, language_level, speaker, scene, text_ee, text_tr, sort_order) VALUES
('st2_arrival', 'tallinn_love', 1, 'A1', 'Anlatici', 'city_day', 'Sa tuled Tallinna. Vanalinn on ilus — kitsad tänavad, vanad majad, ja kõrgel tornid. Ilm on jahe, aga päike paistab.', 'Tallinn''e geliyorsun. Eski şehir güzel — dar sokaklar, eski evler ve yüksek kuleler. Hava serin ama güneş parlıyor.', 1),
('st2_raekoja', 'tallinn_love', 1, 'A1', 'Anlatici', 'city_day', 'Sa jõuad Raekoja platsile. Platsi keskel on vana kaev. Inimesed istuvad kohvikutes ja räägivad. Üks naine jookseb su poole — ta kukub peaaegu!', 'Belediye Meydanına geliyorsun. Meydanın ortasında eski bir kuyu var. İnsanlar kafelerde oturup konuşuyor. Bir kadın sana doğru koşuyor — neredeyse düşecek!', 2),
('st2_meet_liis', 'tallinn_love', 1, 'A1', 'Liis', 'city_day', 'Oi, vabandust! Ma jooksin, sest ma olen hiljaks jäänud! Minu nimi on Liis. Aitäh, et aitasid!', 'Ah, özür dilerim! Koşuyordum çünkü geç kaldım! Benim adım Liis. Yardım ettiğin için teşekkürler!', 3),
('st2_cafe_invite', 'tallinn_love', 1, 'A1', 'Liis', 'city_day', 'Ma lähen kohvikusse! Seal on parim kohv linnas. Kas sa tuled kaasa? Ma ostan sulle kohvi — see on mu tänu!', 'Kafeye gidiyorum! Şehirdeki en iyi kahve orada. Benimle gelir misin? Sana kahve ısmarlayayım — bu benim teşekkürüm!', 4),
('st2_at_cafe', 'tallinn_love', 1, 'A1', 'Anlatici', 'cafe', 'Kohvik on soe ja hubane. Lõhnab kohvi ja värske saia järele. Liis tellib kaks kohvi ja kaks kooki.', 'Kafe sıcak ve rahat. Kahve ve taze ekmek kokuyor. Liis iki kahve ve iki pasta sipariş ediyor.', 5),
('st2_conversation', 'tallinn_love', 1, 'A1', 'Liis', 'cafe', 'Ma olen kunstnik. Ma maalin pilte vanast Tallinnast. Aga täna ma ei saa maalida — mu pintslid on kadunud! Keegi varastas mu koti poes.', 'Ben bir sanatçıyım. Eski Tallinn tabloları yapıyorum. Ama bugün resim yapamıyorum — fırçalarım kayboldu! Birisi mağazada çantamı çaldı.', 6),
('st2_memory_gate_1', 'tallinn_love', 1, 'A1', 'Liis', 'cafe', 'Aga enne otsimist — kas sa mäletad kõiki sõnu, mida me rääkisime?', 'Ama aramaya başlamadan önce — konuştuğumuz tüm kelimeleri hatırlıyor musun?', 7),
('st2_search_market', 'tallinn_love', 1, 'A1', 'Anlatici', 'city_day', 'Te lähete turule. Turg on täis inimesi, värve ja lõhnu. Liis otsib igalt poolt, aga kotti ei ole.', 'Pazara gidiyorsunuz. Pazar insan, renk ve koku dolu. Liis her yerde arıyor ama çanta yok.', 8),
('st2_seller', 'tallinn_love', 1, 'A1', 'Müüja', 'city_day', 'Kott? Jah, ma nägin ühte meest punase kotiga. Ta läks sadama poole. Ta nägi väga kahtlane välja!', 'Çanta mı? Evet, kırmızı çantalı bir adam gördüm. Limana doğru gitti. Çok şüpheli görünüyordu!', 9),
('st2_harbor', 'tallinn_love', 1, 'A1', 'Anlatici', 'river', 'Sadamas on palju laevu. Meri on hall ja tuuline. Sa näed meest punase kotiga — ta istub pingil ja sööb võileiba!', 'Limanda çok gemi var. Deniz gri ve rüzgarlı. Kırmızı çantalı adamı görüyorsun — bankta oturup sandviç yiyor!', 10),
('st2_misunderstanding', 'tallinn_love', 1, 'A1', 'Mees', 'river', 'Mida? See on MINU kott! Ma ostsin selle turult! Aga oota... siin on midagi sees, mis ei ole minu oma.', 'Ne? Bu BENİM çantam! Pazardan aldım! Ama bekle... içinde bana ait olmayan bir şey var.', 11),
('st2_discovery', 'tallinn_love', 1, 'A1', 'Liis', 'river', 'Need on mu vanaema pintslid! Ja siin on kiri... "Kallile Liisile — maali alati südamega. Armastusega, vanaema."', 'Bunlar büyükannemin fırçaları! Ve burada bir mektup var... "Sevgili Liis''e — her zaman kalbiyle resim yap. Sevgiyle, büyükanne."', 12),
('st2_sunset', 'tallinn_love', 1, 'A1', 'Anlatici', 'sunrise_clearing', 'Päike loojub mere taha. Taevas on oranž ja roosa. Liis istub su kõrval ja maalib päikeseloojangut.', 'Güneş denizin arkasına batıyor. Gökyüzü turuncu ve pembe. Liis yanında oturup gün batımını resmediyor.', 13),
('st2_memory_gate_2', 'tallinn_love', 1, 'A1', 'Liis', 'sunrise_clearing', 'Enne kui me hüvasti jätame — kas sa mäletad kõiki sõnu meie seiklusest?', 'Vedalaşmadan önce — maceramızdaki tüm kelimeleri hatırlıyor musun?', 14),
('st2_finale', 'tallinn_love', 1, 'A1', 'Liis', 'night_camp', 'Homme ma maalin uue pildi — ja sina oled sellel pildil. Kas me näeme homme uuesti?', 'Yarın yeni bir tablo yapacağım — ve sen o tabloda olacaksın. Yarın tekrar görüşür müyüz?', 15),
('st2_complete', 'tallinn_love', 1, 'A1', 'Anlatici', 'sunrise_clearing', 'Tallinna Süda — 1. osa on lõppenud. Kas Liis ja sina näete veel? Lugu jätkub...', 'Tallinn''in Kalbi — Bölüm 1 tamamlandı. Liis ile tekrar görüşecek misin? Hikaye devam edecek...', 16);

-- Story 2: Key vocabulary (abbreviated for migration size)
INSERT INTO story_node_vocabulary (story_id, node_id, word, translation, context_hint, sort_order) VALUES
('tallinn_love', 'st2_arrival', 'vanalinn', 'eski şehir', 'Tallinnin tarihi bölgesi', 1),
('tallinn_love', 'st2_arrival', 'tänav', 'sokak', 'Üzerinde yürüdüğün yol', 2),
('tallinn_love', 'st2_arrival', 'torn', 'kule', 'Yüksek, dar yapı', 3),
('tallinn_love', 'st2_arrival', 'ilm', 'hava', 'Dışarıdaki hava durumu', 4),
('tallinn_love', 'st2_raekoja', 'plats', 'meydan', 'Büyük açık alan', 1),
('tallinn_love', 'st2_raekoja', 'inimesed', 'insanlar', 'Birden fazla kişi', 2),
('tallinn_love', 'st2_raekoja', 'naine', 'kadın', 'Sana doğru koşan kişi', 3),
('tallinn_love', 'st2_meet_liis', 'vabandust', 'özür dilerim', 'Hata yaptığında söylersin', 1),
('tallinn_love', 'st2_meet_liis', 'aitäh', 'teşekkürler', 'Minnettarlık ifadesi', 2),
('tallinn_love', 'st2_cafe_invite', 'kohvik', 'kafe', 'Kahve içilen yer', 1),
('tallinn_love', 'st2_cafe_invite', 'kohv', 'kahve', 'Sıcak içecek', 2),
('tallinn_love', 'st2_cafe_invite', 'parim', 'en iyi', 'Hepsinden daha iyi', 3),
('tallinn_love', 'st2_at_cafe', 'soe', 'sıcak', 'Soğuk olmayan', 1),
('tallinn_love', 'st2_at_cafe', 'hubane', 'rahat/samimi', 'İçinizi ısıtan bir ortam', 2),
('tallinn_love', 'st2_at_cafe', 'kook', 'pasta', 'Tatlı yiyecek', 3),
('tallinn_love', 'st2_conversation', 'kunstnik', 'sanatçı', 'Resim yapan kişi', 1),
('tallinn_love', 'st2_conversation', 'pilt', 'resim/tablo', 'Duvarına astığın şey', 2),
('tallinn_love', 'st2_conversation', 'kadunud', 'kayıp', 'Bulunamayan', 3),
('tallinn_love', 'st2_search_market', 'turg', 'pazar', 'Alışveriş yapılan açık alan', 1),
('tallinn_love', 'st2_seller', 'müüja', 'satıcı', 'Bir şey satan kişi', 1),
('tallinn_love', 'st2_seller', 'mees', 'adam/erkek', 'Yetişkin erkek', 2),
('tallinn_love', 'st2_seller', 'sadam', 'liman', 'Gemilerin durduğu yer', 3),
('tallinn_love', 'st2_harbor', 'laev', 'gemi', 'Denizde yüzen büyük araç', 1),
('tallinn_love', 'st2_harbor', 'meri', 'deniz', 'Tuzlu su kütlesi', 2),
('tallinn_love', 'st2_discovery', 'kiri', 'mektup', 'Birine yazdığın mesaj', 1),
('tallinn_love', 'st2_discovery', 'süda', 'kalp', 'Göğsünde atan organ', 2),
('tallinn_love', 'st2_discovery', 'armastus', 'sevgi/aşk', 'Derin bir duygu', 3),
('tallinn_love', 'st2_discovery', 'vanaema', 'büyükanne', 'Annenin annesi', 4),
('tallinn_love', 'st2_sunset', 'oranž', 'turuncu', 'Portakalın rengi', 1),
('tallinn_love', 'st2_sunset', 'roosa', 'pembe', 'Açık kırmızı', 2);

-- Story 2: Choices
INSERT INTO story_node_choices (id, story_id, node_id, text_ee, text_tr, is_correct_grammar, feedback_ee, feedback_tr, next_node_id, sort_order) VALUES
('st2_explore', 'tallinn_love', 'st2_arrival', 'Ma tahan vanalinna uurida!', 'Eski şehri keşfetmek istiyorum!', true, 'Sa kõnnid vanalinna poole.', 'Eski şehre doğru yürüyorsun.', 'st2_raekoja', 1),
('st2_help', 'tallinn_love', 'st2_raekoja', 'Kas kõik on korras?', 'Her şey yolunda mı?', true, 'Naine peatub ja vaatab sind.', 'Kadın duruyor ve sana bakıyor.', 'st2_meet_liis', 1),
('st2_introduce', 'tallinn_love', 'st2_meet_liis', 'Tore tutvuda, Liis! Kuhu sa lähed?', 'Tanıştığımıza memnunum, Liis! Nereye gidiyorsun?', true, 'Liis naeratab.', 'Liis gülümsüyor.', 'st2_cafe_invite', 1),
('st2_yes_cafe', 'tallinn_love', 'st2_cafe_invite', 'Jah, hea meelega!', 'Evet, memnuniyetle!', true, 'Te kõnnite koos kohviku poole.', 'Birlikte kafeye yürüyorsunuz.', 'st2_at_cafe', 1),
('st2_taste', 'tallinn_love', 'st2_at_cafe', 'See kohv on väga hea!', 'Bu kahve çok güzel!', true, 'Liis noogutab rõõmsalt.', 'Liis mutlulukla başını sallıyor.', 'st2_conversation', 1),
('st2_help_find', 'tallinn_love', 'st2_conversation', 'Ma aitan sul neid otsida!', 'Onları bulmana yardım ederim!', true, 'Liisi silmad säravad. "Tõesti?"', 'Liis''in gözleri parlıyor. "Gerçekten mi?"', 'st2_memory_gate_1', 1),
('st2_ask_seller', 'tallinn_love', 'st2_search_market', 'Küsime müüjalt!', 'Satıcıya soralım!', true, 'Hea mõte!', 'İyi fikir!', 'st2_seller', 1),
('st2_go_harbor', 'tallinn_love', 'st2_seller', 'Lähme sadamasse!', 'Limana gidelim!', true, 'Te jooksite sadama poole!', 'Limana doğru koşuyorsunuz!', 'st2_harbor', 1),
('st2_confront', 'tallinn_love', 'st2_harbor', 'See on Liisi kott! Anna see tagasi!', 'Bu Liis''in çantası! Onu geri ver!', true, 'Mees vaatab sind üllatunult.', 'Adam sana şaşkınlıkla bakıyor.', 'st2_misunderstanding', 1),
('st2_look', 'tallinn_love', 'st2_misunderstanding', 'Mis see on?', 'Ne o?', true, 'Mees võtab kotist välja vana karbi.', 'Adam çantadan eski bir kutu çıkarıyor.', 'st2_discovery', 1),
('st2_happy', 'tallinn_love', 'st2_discovery', 'See on imeline!', 'Bu harika!', true, 'Liisi silmis on pisarad — aga rõõmu pisarad.', 'Liis''in gözlerinde yaşlar var — ama mutluluk gözyaşları.', 'st2_sunset', 1),
('st2_stay', 'tallinn_love', 'st2_sunset', 'See on ilus õhtu.', 'Bu güzel bir akşam.', true, 'Liis naeratab. "Jah. Ja sa tegid selle võimalikuks."', 'Liis gülümsüyor. "Evet. Ve sen bunu mümkün kıldın."', 'st2_memory_gate_2', 1),
('st2_end', 'tallinn_love', 'st2_finale', 'Kindlasti, Liis. Head ööd.', 'Kesinlikle, Liis. İyi geceler.', true, 'Liis annab sulle sooja kallistuse.', 'Liis sana sıcak bir sarılma veriyor.', 'st2_complete', 1);

-- Story 2: Minigame gates
INSERT INTO story_node_minigame (story_id, node_id, game_type, target_words, completion_node_id) VALUES
('tallinn_love', 'st2_memory_gate_1', 'MemoryGame', ARRAY['vanalinn','tänav','plats','naine','kohvik','kohv','soe','kook','kunstnik','pilt'], 'st2_search_market'),
('tallinn_love', 'st2_memory_gate_2', 'MemoryGame', ARRAY['turg','müüja','mees','sadam','laev','meri','kiri','süda','armastus','vanaema'], 'st2_finale');


-- ============================================
-- SEED: Story 3 — Kadunud Kiri (Mystery)
-- ============================================
INSERT INTO adventure_stories (id, title, subtitle_tr, subtitle_en, genre_tr, genre_en, emoji, color, cefr_level, sort_order)
VALUES ('lost_letter', 'Kadunud Kiri', 'Eski bir mektup, karanlık bir sır', 'An old letter, a dark secret', 'Gizem', 'Mystery', '🔍', '#4A5A7A', 'A2', 3);

-- (Story 3 nodes abbreviated — using same pattern, key nodes only)
INSERT INTO story_nodes (id, story_id, stage, language_level, speaker, scene, text_ee, text_tr, sort_order) VALUES
('st3_library', 'lost_letter', 2, 'A2', 'Anlatici', 'house_interior', 'Vana raamatukogu on vaikne. Tolm lendab õhus. Sa otsid raamatut Eesti ajaloost, aga leiad midagi muud — vana kiri, peidetud raamatu vahele.', 'Eski kütüphane sessiz. Havada toz uçuyor. Estonya tarihi hakkında bir kitap arıyorsun ama başka bir şey buluyorsun — bir kitabın arasına gizlenmiş eski bir mektup.', 1),
('st3_letter', 'lost_letter', 2, 'A2', 'Anlatici', 'house_interior', '"Kallis sõber, kui sa seda loed, siis ma olen juba läinud. Aare on peidetud kohta, kus valgus ei paista. Otsi torni varju alt. Ära usalda kedagi." — E.V., 1923.', '"Sevgili dostum, bunu okuyorsan, ben artık gitmişimdir. Hazine ışığın ulaşmadığı bir yere gizlendi. Kulenin gölgesinin altında ara. Kimseye güvenme." — E.V., 1923.', 2),
('st3_librarian', 'lost_letter', 2, 'A2', 'Raamatukoguhoidja', 'house_interior', 'Vabandage! Mis te teete? See raamat on väga vana ja väärtuslik!', 'Affedersiniz! Ne yapıyorsunuz? Bu kitap çok eski ve değerli!', 3),
('st3_ev_secret', 'lost_letter', 2, 'A2', 'Raamatukoguhoidja', 'house_interior', 'E.V.? Eduard Vilde? Ta oli kirjanik, kes kadus salapäraselt 1923. aastal. Keegi ei leidnud kunagi tema saladust.', 'E.V.? Eduard Vilde? 1923''te gizemli bir şekilde ortadan kaybolan bir yazardı. Kimse onun sırrını bulamadı.', 4),
('st3_memory_gate_1', 'lost_letter', 2, 'A2', 'Anlatici', 'house_interior', 'Enne edasi minekut — kontrolli oma sõnavara!', 'İlerlemeden önce — kelime bilgini kontrol et!', 5),
('st3_tower_search', 'lost_letter', 2, 'A2', 'Anlatici', 'city_day', 'Sa jõuad kirikutorni juurde. Torni varjus on kitsas trepp, mis viib alla. Sa näed jalatähti tolmus.', 'Kilise kulesine ulaşıyorsun. Kulenin gölgesinde aşağı inen dar bir merdiven var. Tozda ayak izleri görüyorsun.', 6),
('st3_underground', 'lost_letter', 2, 'A2', 'Anlatici', 'dark_awakening', 'Sa oled maa all. Tunnelid lähevad igas suunas. Seinal on vana kaart — kolm teed: "Tõde", "Varandus", "Väljapääs".', 'Yeraltındasın. Tüneller her yöne gidiyor. Duvarda eski bir harita — üç yol: "Gerçek", "Servet", "Çıkış".', 7),
('st3_truth_room', 'lost_letter', 2, 'A2', 'Anlatici', 'dark_awakening', 'Sa leiad väikese toa. Seinal on Eduard Vilde viimane kiri: "Tõeline aare ei ole kuld ega hõbe. See on teadmine."', 'Küçük bir oda buluyorsun. Duvarda Eduard Vilde''nin son mektubu: "Gerçek hazine altın ya da gümüş değil. Bilgidir."', 8),
('st3_memory_gate_2', 'lost_letter', 2, 'A2', 'Anlatici', 'dark_awakening', 'Kast on lukus. Sa pead mäletama õiged sõnad!', 'Sandık kilitli. Doğru kelimeleri hatırlamalısın!', 9),
('st3_finale', 'lost_letter', 2, 'A2', 'Anlatici', 'house_interior', 'Kast avaneb! Sees on Eduard Vilde kadunud käsikirjad — sada aastat vanad. Sa oled leidnud Eesti kirjanduse suurima saladuse.', 'Sandık açılıyor! İçinde Eduard Vilde''nin kayıp el yazmaları — yüz yıllık. Estonya edebiyatının en büyük sırrını buldun.', 10),
('st3_complete', 'lost_letter', 2, 'A2', 'Anlatici', 'sunrise_clearing', 'Kadunud Kiri — 1. osa lõppenud. Eduard Vilde saladus on avastatud. Aga tunnelites on veel palju avastamata...', 'Kayıp Mektup — Bölüm 1 tamamlandı. Ama tünellerde keşfedilecek daha çok şey var...', 11);

INSERT INTO story_node_vocabulary (story_id, node_id, word, translation, context_hint, sort_order) VALUES
('lost_letter', 'st3_library', 'raamatukogu', 'kütüphane', 'Kitapların olduğu yer', 1),
('lost_letter', 'st3_library', 'tolm', 'toz', 'Havada uçan küçük parçacıklar', 2),
('lost_letter', 'st3_library', 'ajalugu', 'tarih', 'Geçmişte olanlar', 3),
('lost_letter', 'st3_library', 'peidetud', 'gizlenmiş', 'Saklanan, görünmeyen', 4),
('lost_letter', 'st3_letter', 'aare', 'hazine', 'Gizli değerli şey', 1),
('lost_letter', 'st3_letter', 'vari', 'gölge', 'Işık engellediğinde oluşan', 2),
('lost_letter', 'st3_ev_secret', 'kirjanik', 'yazar', 'Kitap yazan kişi', 1),
('lost_letter', 'st3_ev_secret', 'kadus', 'kayboldu', 'Bulunamaz hale geldi', 2),
('lost_letter', 'st3_ev_secret', 'saladus', 'sır', 'Gizli bilgi', 3),
('lost_letter', 'st3_tower_search', 'trepp', 'merdiven', 'Yukarı/aşağı çıkma yolu', 1),
('lost_letter', 'st3_underground', 'tunnel', 'tünel', 'Yeraltı geçidi', 1),
('lost_letter', 'st3_underground', 'tõde', 'gerçek/hakikat', 'Yalanın tersi', 2),
('lost_letter', 'st3_underground', 'varandus', 'servet', 'Çok para, zenginlik', 3),
('lost_letter', 'st3_underground', 'väljapääs', 'çıkış', 'Dışarı çıkma yolu', 4),
('lost_letter', 'st3_truth_room', 'kuld', 'altın', 'Değerli sarı metal', 1),
('lost_letter', 'st3_truth_room', 'hõbe', 'gümüş', 'Değerli beyaz metal', 2),
('lost_letter', 'st3_truth_room', 'teadmine', 'bilgi', 'Öğrenilen şeyler', 3),
('lost_letter', 'st3_finale', 'käsikiri', 'el yazması', 'Elle yazılmış belge', 1),
('lost_letter', 'st3_finale', 'kirjandus', 'edebiyat', 'Kitaplar ve yazılar', 2);

INSERT INTO story_node_choices (id, story_id, node_id, text_ee, text_tr, is_correct_grammar, feedback_ee, feedback_tr, next_node_id, sort_order) VALUES
('st3_read', 'lost_letter', 'st3_library', 'Ma loen kirja.', 'Mektubu okuyorum.', true, 'Kiri on vana ja kollane.', 'Mektup eski ve sararmış.', 'st3_letter', 1),
('st3_tower', 'lost_letter', 'st3_letter', 'Torni vari... Kas see on kirikutorn?', 'Kulenin gölgesi... Kilise kulesi mi?', true, 'Kirikutorn paistab kaugelt.', 'Kilise kulesi uzaktan görünüyor.', 'st3_librarian', 1),
('st3_show_letter', 'lost_letter', 'st3_librarian', 'Vaadake, ma leidsin kirja!', 'Bakın, bir mektup buldum!', true, 'Raamatukoguhoidja nägu muutub kahvatuks.', 'Kütüphanecinin yüzü sararıyor.', 'st3_ev_secret', 1),
('st3_investigate', 'lost_letter', 'st3_ev_secret', 'Ma tahan seda saladust uurida!', 'Bu sırrı araştırmak istiyorum!', true, '"Olge ettevaatlik."', '"Dikkatli olun."', 'st3_memory_gate_1', 1),
('st3_descend', 'lost_letter', 'st3_tower_search', 'Ma lähen alla.', 'Aşağı iniyorum.', true, 'Trepp on pime ja kitsas.', 'Merdiven karanlık ve dar.', 'st3_underground', 1),
('st3_truth', 'lost_letter', 'st3_underground', 'Ma valin tõe.', 'Gerçeği seçiyorum.', true, 'Sa kõnnid tõe tunnelisse.', 'Gerçek tüneline giriyorsun.', 'st3_truth_room', 1),
('st3_find_books', 'lost_letter', 'st3_truth_room', 'Kus on raamatud?', 'Kitaplar nerede?', true, 'Sa näed vana kasti nurgas.', 'Köşede eski bir sandık görüyorsun.', 'st3_memory_gate_2', 1),
('st3_end', 'lost_letter', 'st3_finale', 'See on uskumatu!', 'Bu inanılmaz!', true, 'Raamatukoguhoidja nutab rõõmust.', 'Kütüphaneci mutluluktan ağlıyor.', 'st3_complete', 1);

INSERT INTO story_node_minigame (story_id, node_id, game_type, target_words, completion_node_id) VALUES
('lost_letter', 'st3_memory_gate_1', 'MemoryGame', ARRAY['raamatukogu','ajalugu','peidetud','aare','vari','kirjanik','saladus','kadus'], 'st3_tower_search'),
('lost_letter', 'st3_memory_gate_2', 'MemoryGame', ARRAY['trepp','tunnel','tõde','varandus','väljapääs','kuld','hõbe','teadmine'], 'st3_finale');


-- ============================================
-- SEED: Story 4 — Tähtedevaheline (Sci-Fi)
-- ============================================
INSERT INTO adventure_stories (id, title, subtitle_tr, subtitle_en, genre_tr, genre_en, emoji, color, cefr_level, sort_order)
VALUES ('among_stars', 'Tähtedevaheline', 'Uzay gemisinde uyanmak, yıldızlar arasında hayatta kalmak', 'Waking up on a spaceship, surviving among the stars', 'Bilim Kurgu', 'Sci-Fi', '🚀', '#3A5A8A', 'A2', 4);

INSERT INTO story_nodes (id, story_id, stage, language_level, speaker, scene, text_ee, text_tr, sort_order) VALUES
('st4_wake_ship', 'among_stars', 2, 'A2', 'Anlatici', 'spaceship', 'Sa ärkad. Pea valutab. Sa ei mäleta midagi. Ümberringi on metallseinad ja vilkuvad tuled. Sa oled kosmoselaeval.', 'Uyanıyorsun. Başın ağrıyor. Hiçbir şey hatırlamıyorsun. Etrafında metal duvarlar ve yanıp sönen ışıklar var. Bir uzay gemsindesin.', 1),
('st4_kai', 'among_stars', 2, 'A2', 'K.A.I.', 'spaceship', 'Tere tulemast tagasi, Kapten. Mina olen K.A.I. — laeva tehisintellekt. Sa olid külmunud kaks aastat. Me oleme kaugel Maast.', 'Tekrar hoş geldin, Kaptan. Ben K.A.I. — geminin yapay zekasıyım. İki yıldır dondurulmuş haldeydin. Dünyadan çok uzaktayız.', 2),
('st4_problem', 'among_stars', 2, 'A2', 'K.A.I.', 'spaceship', 'Meil on probleem. Laeva kütus saab otsa kolme päeva pärast. Lähedal on üks planeet — seal võib olla kütust. Aga seal on ka signaal...', 'Bir sorunumuz var. Geminin yakıtı üç gün içinde bitecek. Yakınlarda bir gezegen var — orada yakıt olabilir. Ama orada bir sinyal de var...', 3),
('st4_landing', 'among_stars', 2, 'A2', 'Anlatici', 'sunrise_clearing', 'Laev maandub planeedi pinnal. Õhk on hingav. Taevas on kaks päikest — üks sinine, üks punane. Maapind on kaetud kristallidega.', 'Gemi gezegenin yüzeyine iniyor. Hava solunabilir. Gökyüzünde iki güneş var — biri mavi, biri kırmızı. Zemin kristallerle kaplı.', 4),
('st4_memory_gate_1', 'among_stars', 2, 'A2', 'K.A.I.', 'sunrise_clearing', 'Kapten, enne edasi liikumist — kontrolli oma teadmisi!', 'Kaptan, ilerlemeden önce — bilgilerini kontrol et!', 5),
('st4_alien_ruins', 'among_stars', 2, 'A2', 'Anlatici', 'dark_awakening', 'Sa leiad vanad varemed. Need ei ole inimeste ehitatud. Seinad on kaetud sümbolitega.', 'Eski kalıntılar buluyorsun. İnsanlar tarafından yapılmamış. Duvarlar sembollerle kaplı.', 6),
('st4_activation', 'among_stars', 2, 'A2', 'K.A.I.', 'spaceship', 'Signaal on muutunud! See ütleb: "Tere tulemast. Me oleme oodanud."', 'Sinyal değişti! Diyor ki: "Hoş geldiniz. Sizi bekliyorduk."', 7),
('st4_ancient', 'among_stars', 2, 'A2', 'K.A.I.', 'dark_awakening', 'See tsivilisatsioon on välja surnud tuhat aastat tagasi. Aga nad jätsid sõnumi — ja kütust.', 'Bu medeniyet bin yıl önce yok olmuş. Ama bir mesaj bırakmışlar — ve yakıt.', 8),
('st4_memory_gate_2', 'among_stars', 2, 'A2', 'K.A.I.', 'spaceship', 'Enne otsustamist — kontrolli oma mälu!', 'Karar vermeden önce — hafızanı kontrol et!', 9),
('st4_finale', 'among_stars', 2, 'A2', 'K.A.I.', 'spaceship', '"Te ei ole üksi universumis. Tuleviku võti on keeles." K.A.I. seab kursi uue planeedi poole.', '"Evrende yalnız değilsiniz. Geleceğin anahtarı dildedir." K.A.I. yeni bir gezegene rota çiziyor.', 10),
('st4_complete', 'among_stars', 2, 'A2', 'Anlatici', 'spaceship', 'Tähtedevaheline — 1. osa lõppenud. Mis ootab teid järgmisel planeedil?', 'Yıldızlararası — Bölüm 1 tamamlandı. Bir sonraki gezegende sizi ne bekliyor?', 11);

INSERT INTO story_node_vocabulary (story_id, node_id, word, translation, context_hint, sort_order) VALUES
('among_stars', 'st4_wake_ship', 'pea', 'baş/kafa', 'Vücudunun üst kısmı', 1),
('among_stars', 'st4_wake_ship', 'valutab', 'ağrıyor', 'Acı hissetmek', 2),
('among_stars', 'st4_wake_ship', 'kosmoselaev', 'uzay gemisi', 'Yıldızlar arasında yolculuk', 3),
('among_stars', 'st4_kai', 'kapten', 'kaptan', 'Geminin lideri', 1),
('among_stars', 'st4_kai', 'tehisintellekt', 'yapay zeka', 'Düşünebilen bilgisayar', 2),
('among_stars', 'st4_kai', 'külmunud', 'dondurulmuş', 'Çok soğuk halde', 3),
('among_stars', 'st4_kai', 'Maa', 'Dünya', 'Bizim gezegenimiz', 4),
('among_stars', 'st4_problem', 'kütus', 'yakıt', 'Gemiyi çalıştıran enerji', 1),
('among_stars', 'st4_problem', 'planeet', 'gezegen', 'Yıldızın etrafında dönen', 2),
('among_stars', 'st4_problem', 'signaal', 'sinyal', 'İletişim işareti', 3),
('among_stars', 'st4_landing', 'pind', 'yüzey', 'Bir şeyin dış kısmı', 1),
('among_stars', 'st4_landing', 'õhk', 'hava', 'Nefes aldığın gaz', 2),
('among_stars', 'st4_landing', 'kristall', 'kristal', 'Parlak, şeffaf mineral', 3),
('among_stars', 'st4_alien_ruins', 'varemed', 'kalıntılar', 'Eski yıkılmış yapılar', 1),
('among_stars', 'st4_alien_ruins', 'sümbol', 'sembol', 'Bir anlam taşıyan işaret', 2),
('among_stars', 'st4_activation', 'muutunud', 'değişmiş', 'Farklı hale gelmiş', 1),
('among_stars', 'st4_activation', 'oodanud', 'beklemiş', 'Sabırla zaman geçirmiş', 2),
('among_stars', 'st4_ancient', 'tsivilisatsioon', 'medeniyet', 'Gelişmiş toplum', 1),
('among_stars', 'st4_ancient', 'sõnum', 'mesaj', 'Birine gönderilen bilgi', 2),
('among_stars', 'st4_ancient', 'tuhat', 'bin', '1000 sayısı', 3),
('among_stars', 'st4_finale', 'universum', 'evren', 'Her şeyin içinde olduğu alan', 1),
('among_stars', 'st4_finale', 'tulevik', 'gelecek', 'Yarından sonraki zaman', 2),
('among_stars', 'st4_finale', 'võti', 'anahtar', 'Bir kapıyı açan nesne', 3),
('among_stars', 'st4_finale', 'keel', 'dil', 'İletişim aracı', 4);

INSERT INTO story_node_choices (id, story_id, node_id, text_ee, text_tr, is_correct_grammar, feedback_ee, feedback_tr, next_node_id, sort_order) VALUES
('st4_look', 'among_stars', 'st4_wake_ship', 'Kus ma olen?', 'Ben neredeyim?', true, 'Keegi vastab raadio kaudu.', 'Birisi telsizden cevap veriyor.', 'st4_kai', 1),
('st4_why', 'among_stars', 'st4_kai', 'Miks ma ärkasin?', 'Neden uyandım?', true, 'K.A.I. vaikib hetkeks.', 'K.A.I. bir an sessiz kalıyor.', 'st4_problem', 1),
('st4_land', 'among_stars', 'st4_problem', 'Me peame maanduma!', 'İniş yapmalıyız!', true, 'K.A.I. alustab maandumist.', 'K.A.I. inişe başlıyor.', 'st4_landing', 1),
('st4_explore', 'among_stars', 'st4_landing', 'Lähme uurima!', 'Keşfe çıkalım!', true, 'Kristallid helendavad su sammude all.', 'Kristaller adımların altında parıldıyor.', 'st4_memory_gate_1', 1),
('st4_touch', 'among_stars', 'st4_alien_ruins', 'Ma puudutan sümboleid.', 'Sembollere dokunuyorum.', true, 'Sümbolid hakkavad helendama!', 'Semboller parlamaya başlıyor!', 'st4_activation', 1),
('st4_respond', 'among_stars', 'st4_activation', 'Vasta neile!', 'Onlara cevap ver!', true, '"Aga Kapten... signaal on tuhat aastat vana."', '"Ama Kaptan... sinyal bin yıl önce gönderilmiş."', 'st4_ancient', 1),
('st4_take_fuel', 'among_stars', 'st4_ancient', 'Me võtame kütuse.', 'Yakıtı alalım.', true, '"Aga Maa ei ole enam ohutu."', '"Ama Dünya artık güvenli değil."', 'st4_memory_gate_2', 1),
('st4_end', 'among_stars', 'st4_finale', 'Me peame edasi õppima.', 'Öğrenmeye devam etmeliyiz.', true, 'Seiklus jätkub...', 'Macera devam ediyor...', 'st4_complete', 1);

INSERT INTO story_node_minigame (story_id, node_id, game_type, target_words, completion_node_id) VALUES
('among_stars', 'st4_memory_gate_1', 'MemoryGame', ARRAY['kosmoselaev','kapten','kütus','planeet','signaal','pind','õhk','kristall'], 'st4_alien_ruins'),
('among_stars', 'st4_memory_gate_2', 'MemoryGame', ARRAY['varemed','sümbol','muutunud','oodanud','tsivilisatsioon','sõnum','tuhat'], 'st4_finale');


-- ============================================
-- SEED: Story 5 — Metsa Saladused (Thriller)
-- ============================================
INSERT INTO adventure_stories (id, title, subtitle_tr, subtitle_en, genre_tr, genre_en, emoji, color, cefr_level, sort_order)
VALUES ('forest_secrets', 'Metsa Saladused', 'Terk edilmiş köyde karanlık bir geçmiş', 'A dark past in an abandoned village', 'Gerilim', 'Thriller', '🌲', '#3A4A3A', 'B1', 5);

INSERT INTO story_nodes (id, story_id, stage, language_level, speaker, scene, text_ee, text_tr, sort_order) VALUES
('st5_lost', 'forest_secrets', 3, 'B1', 'Anlatici', 'forest_day', 'Sa oled metsas. Sa tulid matkale, aga kaotasid raja ära. Telefonis ei ole levi. Päike hakkab loojuma.', 'Ormandasın. Yürüyüşe çıkmıştın ama yolu kaybettin. Telefonda çekim yok. Güneş batmaya başlıyor.', 1),
('st5_sounds', 'forest_secrets', 3, 'B1', 'Anlatici', 'dark_awakening', 'Sa kuuled häält — see on nagu laulmine, aga sõnadeta. See tuleb sügavalt metsast. Ja siis — valgus!', 'Bir ses duyuyorsun — şarkı söyleme gibi ama kelimesiz. Ormanın derinliklerinden geliyor. Ve sonra — ışık!', 2),
('st5_village', 'forest_secrets', 3, 'B1', 'Anlatici', 'house_exterior', 'Sa jõuad väikesesse külla. Majad on tühjad. Uksed on lahti. Toidulauad on kaetud, aga tolm on kõikjal.', 'Küçük bir köye ulaşıyorsun. Evler boş. Kapılar açık. Yemek masaları hazırlanmış ama her yer tozlu.', 3),
('st5_inside_house', 'forest_secrets', 3, 'B1', 'Anlatici', 'house_interior', 'Seinal on perepilt: ema, isa ja kaks last. Pilt on dateeritud: 1987. Ja laual on avatud päevik.', 'Duvarda bir aile fotoğrafı: anne, baba ve iki çocuk. Fotoğrafın tarihi: 1987. Ve masada açık bir günlük var.', 4),
('st5_diary', 'forest_secrets', 3, 'B1', 'Anlatici', 'house_interior', '"Hääled tulevad jälle. Lapsed kardavad. Mets ei lase meid lahkuda. Meri ütleb, et peame ohverdama midagi, mida armastame."', '"Sesler yine geliyor. Çocuklar korkuyor. Orman bizi bırakmıyor. Meri diyor ki sevdiğimiz bir şeyi feda etmeliyiz."', 5),
('st5_memory_gate_1', 'forest_secrets', 3, 'B1', 'Anlatici', 'dark_awakening', 'Enne edasi minekut pead sa meeles pidama kõike, mida oled õppinud.', 'İlerlemeden önce öğrendiğin her şeyi hatırlamalısın.', 6),
('st5_the_voice', 'forest_secrets', 3, 'B1', '???', 'storm', 'Sa ei pea kartma. Ma ei ole su vaenlane. Ma olen mets. Ma olen elanud siin tuhandeid aastaid. Ja ma olen üksik.', 'Korkmana gerek yok. Ben senin düşmanın değilim. Ben ormanım. Binlerce yıldır burada yaşıyorum. Ve yalnızım.', 7),
('st5_forest_truth', 'forest_secrets', 3, 'B1', 'Mets', 'forest_day', 'Sest nad ei kuula. Nad tulevad, võtavad puid, tapavad loomi ja lähevad ära. Keegi ei räägi minuga. Ma tahtsin lihtsalt sõpra.', 'Çünkü dinlemiyorlar. Geliyorlar, ağaçları kesiyorlar, hayvanları öldürüyorlar ve gidiyorlar. Kimse benimle konuşmuyor. Ben sadece bir arkadaş istiyordum.', 8),
('st5_resolution', 'forest_secrets', 3, 'B1', 'Mets', 'sunrise_clearing', 'Sa oled esimene, kes kuulab. Ma lasen sind lahkuda. Aga tule tagasi. Õpeta teistele, et mets on elus.', 'Dinleyen ilk kişisin. Gitmene izin vereceğim. Ama geri dön. Başkalarına öğret ki orman canlıdır.', 9),
('st5_memory_gate_2', 'forest_secrets', 3, 'B1', 'Mets', 'forest_day', 'Enne lahkumist — näita, et sa mäletad mu sõnu.', 'Ayrılmadan önce — kelimelerimi hatırladığını göster.', 10),
('st5_finale', 'forest_secrets', 3, 'B1', 'Anlatici', 'sunrise_clearing', 'Sa kõnnid välja metsast. Päike tõuseb. Su telefon näitab jälle levi. Aga sa tead nüüd midagi, mida teised ei tea.', 'Ormandan çıkıyorsun. Güneş doğuyor. Telefonunda tekrar çekim var. Ama artık başkalarının bilmediği bir şey biliyorsun.', 11),
('st5_complete', 'forest_secrets', 3, 'B1', 'Anlatici', 'forest_day', 'Metsa Saladused — 1. osa lõppenud. Mets ootab... Aga mis juhtus 1987. aasta perekonnaga?', 'Ormanın Sırları — Bölüm 1 tamamlandı. Orman bekliyor... Ama 1987''deki aileye ne oldu?', 12);

INSERT INTO story_node_vocabulary (story_id, node_id, word, translation, context_hint, sort_order) VALUES
('forest_secrets', 'st5_lost', 'matk', 'yürüyüş', 'Doğada yürüme', 1),
('forest_secrets', 'st5_lost', 'raja', 'yol/patika', 'Ormanda yürüme yolu', 2),
('forest_secrets', 'st5_lost', 'levi', 'çekim/sinyal', 'Telefonun bağlantısı', 3),
('forest_secrets', 'st5_sounds', 'hääl', 'ses', 'Kulaklarınla duyduğun', 1),
('forest_secrets', 'st5_sounds', 'laulmine', 'şarkı söyleme', 'Melodiyle ses çıkarma', 2),
('forest_secrets', 'st5_sounds', 'sügavalt', 'derinlerden', 'İçeriden, uzaktan', 3),
('forest_secrets', 'st5_sounds', 'nõrk', 'zayıf', 'Güçlü olmayan', 4),
('forest_secrets', 'st5_village', 'küla', 'köy', 'Küçük yerleşim yeri', 1),
('forest_secrets', 'st5_village', 'tühi', 'boş', 'İçinde hiçbir şey olmayan', 2),
('forest_secrets', 'st5_village', 'lahkunud', 'ayrılmış/gitmiş', 'Oradan uzaklaşmış', 3),
('forest_secrets', 'st5_inside_house', 'perepilt', 'aile fotoğrafı', 'Aileyi gösteren resim', 1),
('forest_secrets', 'st5_inside_house', 'riided', 'kıyafetler', 'Giydiğin şeyler', 2),
('forest_secrets', 'st5_inside_house', 'päevik', 'günlük', 'Her gün yazdığın defter', 3),
('forest_secrets', 'st5_diary', 'kardavad', 'korkuyorlar', 'Korku hissediyorlar', 1),
('forest_secrets', 'st5_diary', 'lahkuda', 'ayrılmak', 'Bir yerden gitmek', 2),
('forest_secrets', 'st5_diary', 'ohverdama', 'feda etmek', 'Değerli bir şeyden vazgeçmek', 3),
('forest_secrets', 'st5_diary', 'armastame', 'seviyoruz', 'Derin bir bağ hissetmek', 4),
('forest_secrets', 'st5_the_voice', 'vaenlane', 'düşman', 'Sana karşı olan', 1),
('forest_secrets', 'st5_the_voice', 'üksik', 'yalnız', 'Tek başına olan', 2),
('forest_secrets', 'st5_the_voice', 'tuhandeid', 'binlerce', 'Çok sayıda bin', 3),
('forest_secrets', 'st5_forest_truth', 'kuula', 'dinle', 'Kulaklarınla dikkat et', 1),
('forest_secrets', 'st5_forest_truth', 'loomad', 'hayvanlar', 'Canlı yaratıklar', 2),
('forest_secrets', 'st5_forest_truth', 'tunnen', 'hissediyorum', 'İç dünyamda yaşadığım', 3),
('forest_secrets', 'st5_forest_truth', 'sõpra', 'arkadaş', 'Birlikte vakit geçiren', 4),
('forest_secrets', 'st5_resolution', 'esimene', 'ilk', 'Birinci olan', 1),
('forest_secrets', 'st5_resolution', 'elus', 'canlı/yaşayan', 'Hayatı olan', 2),
('forest_secrets', 'st5_resolution', 'tagasi', 'geri', 'Geldiğin yere doğru', 3);

INSERT INTO story_node_choices (id, story_id, node_id, text_ee, text_tr, is_correct_grammar, feedback_ee, feedback_tr, next_node_id, sort_order) VALUES
('st5_forward', 'forest_secrets', 'st5_lost', 'Ma lähen edasi.', 'İleri gidiyorum.', true, 'Puud muutuvad tihedamaks.', 'Ağaçlar daha sık hale geliyor.', 'st5_sounds', 1),
('st5_stay', 'forest_secrets', 'st5_lost', 'Ma jään paigale.', 'Yerinde kalıyorum.', true, 'Sa istud maha. Aga siis kuuled midagi...', 'Oturuyorsun. Ama sonra bir şey duyuyorsun...', 'st5_sounds', 2),
('st5_follow', 'forest_secrets', 'st5_sounds', 'Ma järgin valgust.', 'Işığı takip ediyorum.', true, 'Valgus viib sind läbi puude...', 'Işık seni ağaçların arasından geçiriyor...', 'st5_village', 1),
('st5_enter', 'forest_secrets', 'st5_village', 'Ma lähen ühte majja sisse.', 'Evlerden birine giriyorum.', true, 'Uks kriuksub.', 'Kapı gıcırdıyor.', 'st5_inside_house', 1),
('st5_read_diary', 'forest_secrets', 'st5_inside_house', 'Ma loen päevikut.', 'Günlüğü okuyorum.', true, 'Viimane sissekanne on 1987.', 'Son giriş 1987 tarihli.', 'st5_diary', 1),
('st5_scared', 'forest_secrets', 'st5_diary', 'Mis juhtus nendega?', 'Onlara ne oldu?', true, 'Sa kuuled häält jälle.', 'O sesi yine duyuyorsun.', 'st5_memory_gate_1', 1),
('st5_talk', 'forest_secrets', 'st5_the_voice', 'Miks sa hoiad inimesi siin?', 'Neden insanları burada tutuyorsun?', true, 'Mets vaikib kauaks.', 'Orman uzun süre susuyor.', 'st5_forest_truth', 1),
('st5_befriend', 'forest_secrets', 'st5_forest_truth', 'Ma olen sinu sõber.', 'Ben senin arkadaşınım.', true, 'Puud liiguvad. Tuul vaibub.', 'Ağaçlar hareket ediyor. Rüzgar diniyor.', 'st5_resolution', 1),
('st5_promise', 'forest_secrets', 'st5_resolution', 'Ma luban. Ma tulen tagasi.', 'Söz veriyorum. Geri döneceğim.', true, 'Puud avanevad su ees.', 'Ağaçlar önünde açılıyor.', 'st5_memory_gate_2', 1),
('st5_end', 'forest_secrets', 'st5_finale', 'Ma tulen tagasi, mets.', 'Geri döneceğim, orman.', true, 'Kaugelt kuuled sa metsa laulmas.', 'Uzaktan ormanın şarkı söylediğini duyuyorsun.', 'st5_complete', 1);

INSERT INTO story_node_minigame (story_id, node_id, game_type, target_words, completion_node_id) VALUES
('forest_secrets', 'st5_memory_gate_1', 'MemoryGame', ARRAY['matk','raja','hääl','nõrk','küla','tühi','lahkunud','päevik','kardavad','ohverdama'], 'st5_the_voice'),
('forest_secrets', 'st5_memory_gate_2', 'MemoryGame', ARRAY['vaenlane','üksik','kuula','loomad','tunnen','sõpra','esimene','elus','tagasi'], 'st5_finale');

-- Try to link vocabulary words to existing words table
UPDATE story_node_vocabulary SET word_id = w.id
FROM words w WHERE story_node_vocabulary.word = w.estonian AND story_node_vocabulary.word_id IS NULL;
