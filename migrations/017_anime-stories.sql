-- Anime-inspired adventure stories for Estonian language learning

-- ============================================
-- Story 6: Küla Kaitsja (Naruto-inspired) A1
-- An outcast in a village trains to become its protector
-- ============================================
INSERT INTO adventure_stories (id, title, subtitle_tr, subtitle_en, genre_tr, genre_en, emoji, color, cefr_level, sort_order)
VALUES ('village_hero', 'Küla Kaitsja', 'Dışlanan bir çocuk köyün en güçlü koruyucusu olmayı hayal ediyor', 'An outcast child dreams of becoming the village''s strongest protector', 'Aksiyon', 'Action', '🍥', '#E8720C', 'A1', 6);

INSERT INTO story_nodes (id, story_id, stage, language_level, speaker, scene, text_ee, text_tr, sort_order) VALUES
('n1_alone', 'village_hero', 1, 'A1', 'Anlatici', 'city_day', 'Väikeses külas elab poiss nimega Taru. Tal ei ole sõpru. Teised lapsed ei mängi temaga. Aga Tarul on unistus — ta tahab saada küla kõige tugevamaks kaitsjaks.', 'Küçük bir köyde Taru adında bir çocuk yaşıyor. Arkadaşı yok. Diğer çocuklar onunla oynamıyor. Ama Taru''nun bir hayali var — köyün en güçlü koruyucusu olmak istiyor.', 1),
('n1_master', 'village_hero', 1, 'A1', 'Taru', 'forest_day', 'Ma tahan treenida! Aga keegi ei taha mind õpetada. Kõik ütlevad, et ma olen liiga nõrk. Ma näitan neile!', 'Antrenman yapmak istiyorum! Ama kimse bana öğretmek istemiyor. Herkes çok zayıf olduğumu söylüyor. Onlara göstereceğim!', 2),
('n1_old_man', 'village_hero', 1, 'A1', 'Vana mees', 'house_exterior', 'Tere, poiss. Miks sa oled üksi metsas? Sa treenid? Hmm... ma näen midagi sinus. Tule, ma õpetan sind.', 'Merhaba çocuk. Neden ormanda yalnızsın? Antrenman mı yapıyorsun? Hmm... sende bir şey görüyorum. Gel, sana öğreteyim.', 3),
('n1_training', 'village_hero', 1, 'A1', 'Vana mees', 'forest_day', 'Esimene reegel: tugev keha vajab tugevat vaimu. Jookse! Hüppa! Roni puusse! Iga päev, päikesest päikeseloojanguni.', 'İlk kural: güçlü bir vücut güçlü bir zihin gerektirir. Koş! Zıpla! Ağaca tırman! Her gün, güneş doğumundan batımına kadar.', 4),
('n1_months', 'village_hero', 1, 'A1', 'Anlatici', 'sunrise_clearing', 'Kuud mööduvad. Taru jookseb iga päev kiiremini. Ta tõstab raskemaid kive. Ta ronib kõrgemale. Aga külas naeravad lapsed ikka tema üle.', 'Aylar geçiyor. Taru her gün daha hızlı koşuyor. Daha ağır taşları kaldırıyor. Daha yükseğe tırmanıyor. Ama köyde çocuklar hâlâ onunla alay ediyor.', 5),
('n1_gate1', 'village_hero', 1, 'A1', 'Vana mees', 'forest_day', 'Enne järgmist sammu — kas sa mäletad, mida sa oled õppinud?', 'Bir sonraki adımdan önce — öğrendiklerini hatırlıyor musun?', 6),
('n1_attack', 'village_hero', 1, 'A1', 'Anlatici', 'storm', 'Ühel ööl tuleb torm. Aga see ei ole tavaline torm — metsast tulevad suured hundid! Nad ründavad küla! Inimesed karjuvad ja jooksevad.', 'Bir gece fırtına geliyor. Ama bu sıradan bir fırtına değil — ormandan büyük kurtlar geliyor! Köye saldırıyorlar! İnsanlar bağırıp kaçıyor.', 7),
('n1_stand', 'village_hero', 1, 'A1', 'Taru', 'storm', 'Ma ei jookse ära! See on MINU küla! Need inimesed... nad ei ole mu sõbrad, aga nad on mu inimesed. Ma kaitsen neid!', 'Kaçmayacağım! Burası BENİM köyüm! Bu insanlar... arkadaşlarım değiller ama benim insanlarım. Onları koruyacağım!', 8),
('n1_fight', 'village_hero', 1, 'A1', 'Anlatici', 'storm', 'Taru seisab hundide ees. Ta on väike, aga ta ei karda. Vana mehe treening on teda muutnud. Ta on tugev. Ta on kiire. Ta on valmis.', 'Taru kurtların önünde duruyor. Küçük ama korkmuyor. Yaşlı adamın antrenmanı onu değiştirdi. Güçlü. Hızlı. Hazır.', 9),
('n1_victory', 'village_hero', 1, 'A1', 'Anlatici', 'sunrise_clearing', 'Hundid põgenevad! Taru on päästnud küla! Inimesed vaatavad teda — esimest korda mitte vihaga, vaid imetlusega. Üks väike tüdruk tuleb tema juurde.', 'Kurtlar kaçıyor! Taru köyü kurtardı! İnsanlar ona bakıyor — ilk kez öfkeyle değil, hayranlıkla. Küçük bir kız yanına geliyor.', 10),
('n1_friend', 'village_hero', 1, 'A1', 'Väike tüdruk', 'sunrise_clearing', 'Sa oled kangelane! Kas... kas sa oled minu sõber?', 'Sen bir kahramansın! Sen... benim arkadaşım olur musun?', 11),
('n1_gate2', 'village_hero', 1, 'A1', 'Anlatici', 'sunrise_clearing', 'Enne lõppu — kas sa mäletad kõiki sõnu?', 'Bitirmeden önce — tüm kelimeleri hatırlıyor musun?', 12),
('n1_end', 'village_hero', 1, 'A1', 'Taru', 'city_day', 'Jah. Ma olen sinu sõber. Ja ühel päeval... ma olen küla kaitsja. See on alles algus!', 'Evet. Ben senin arkadaşınım. Ve bir gün... köyün koruyucusu olacağım. Bu daha başlangıç!', 13),
('n1_complete', 'village_hero', 1, 'A1', 'Anlatici', 'sunrise_clearing', 'Küla Kaitsja — 1. osa lõppenud. Taru teekond on alles alanud. Kes on salapärane vana mees? Miks ründasid hundid küla?', 'Köyün Koruyucusu — Bölüm 1 tamamlandı. Taru''nun yolculuğu daha yeni başladı. Gizemli yaşlı adam kim? Kurtlar neden köye saldırdı?', 14);

INSERT INTO story_node_vocabulary (story_id, node_id, word, translation, context_hint, sort_order) VALUES
('village_hero', 'n1_alone', 'poiss', 'çocuk/oğlan', 'Erkek çocuk', 1),
('village_hero', 'n1_alone', 'unistus', 'hayal/rüya', 'Gerçekleşmesini istediğin şey', 2),
('village_hero', 'n1_alone', 'tugev', 'güçlü', 'Kuvvetli olan', 3),
('village_hero', 'n1_alone', 'kaitsja', 'koruyucu', 'Başkalarını koruyan kişi', 4),
('village_hero', 'n1_master', 'treenida', 'antrenman yapmak', 'Egzersiz yapmak', 1),
('village_hero', 'n1_master', 'õpetada', 'öğretmek', 'Bilgi aktarmak', 2),
('village_hero', 'n1_master', 'nõrk', 'zayıf', 'Güçlü olmayan', 3),
('village_hero', 'n1_old_man', 'mets', 'orman', 'Ağaçlarla dolu yer', 1),
('village_hero', 'n1_training', 'keha', 'vücut', 'Fiziksel bedenin', 1),
('village_hero', 'n1_training', 'vaim', 'zihin/ruh', 'Düşünen parçan', 2),
('village_hero', 'n1_training', 'jooksma', 'koşmak', 'Hızlı hareket etmek', 3),
('village_hero', 'n1_training', 'hüppama', 'zıplamak', 'Havaya sıçramak', 4),
('village_hero', 'n1_training', 'ronima', 'tırmanmak', 'Yukarı çıkmak', 5),
('village_hero', 'n1_months', 'kiiremini', 'daha hızlı', 'Daha çabuk', 1),
('village_hero', 'n1_months', 'raskemaid', 'daha ağır', 'Daha fazla ağırlıkta', 2),
('village_hero', 'n1_months', 'kõrgemale', 'daha yükseğe', 'Daha yukarıya', 3),
('village_hero', 'n1_attack', 'torm', 'fırtına', 'Şiddetli hava olayı', 1),
('village_hero', 'n1_attack', 'hunt', 'kurt', 'Vahşi köpek benzeri hayvan', 2),
('village_hero', 'n1_attack', 'ründama', 'saldırmak', 'Birine zarar vermeye çalışmak', 3),
('village_hero', 'n1_attack', 'karjuma', 'bağırmak', 'Yüksek sesle konuşmak', 4),
('village_hero', 'n1_stand', 'kaitsma', 'korumak', 'Güvende tutmak', 1),
('village_hero', 'n1_fight', 'kiire', 'hızlı', 'Çabuk hareket eden', 1),
('village_hero', 'n1_fight', 'valmis', 'hazır', 'Yapabilecek durumda', 2),
('village_hero', 'n1_victory', 'kangelane', 'kahraman', 'Başkalarını kurtaran kişi', 1),
('village_hero', 'n1_victory', 'imetlus', 'hayranlık', 'Birine hayran olmak', 2),
('village_hero', 'n1_friend', 'sõber', 'arkadaş', 'Birlikte vakit geçirdiğin kişi', 1);

INSERT INTO story_node_choices (id, story_id, node_id, text_ee, text_tr, is_correct_grammar, feedback_ee, feedback_tr, next_node_id, sort_order) VALUES
('n1_c1', 'village_hero', 'n1_alone', 'Ma tahan teada rohkem!', 'Daha fazla bilmek istiyorum!', true, 'Taru lugu algab.', 'Taru''nun hikayesi başlıyor.', 'n1_master', 1),
('n1_c2', 'village_hero', 'n1_master', 'Ma näitan neile!', 'Onlara göstereceğim!', true, 'Sa jooksed metsa poole.', 'Ormana doğru koşuyorsun.', 'n1_old_man', 1),
('n1_c3', 'village_hero', 'n1_old_man', 'Palun õpetage mind!', 'Lütfen bana öğretin!', true, 'Vana mees naeratab. "Hästi."', 'Yaşlı adam gülümsüyor. "Pekala."', 'n1_training', 1),
('n1_c4', 'village_hero', 'n1_training', 'Ma olen valmis!', 'Hazırım!', true, 'Treening algab.', 'Antrenman başlıyor.', 'n1_months', 1),
('n1_c5', 'village_hero', 'n1_months', 'Ma ei anna alla!', 'Pes etmeyeceğim!', true, 'Sa treenid edasi.', 'Antrenmanına devam ediyorsun.', 'n1_gate1', 1),
('n1_c6', 'village_hero', 'n1_attack', 'Ma pean midagi tegema!', 'Bir şey yapmalıyım!', true, 'Sa jooksed küla poole.', 'Köye doğru koşuyorsun.', 'n1_stand', 1),
('n1_c7', 'village_hero', 'n1_stand', 'Ma kaitsen neid!', 'Onları koruyacağım!', true, 'Sa seisad hundide ees.', 'Kurtların önünde duruyorsun.', 'n1_fight', 1),
('n1_c8', 'village_hero', 'n1_fight', 'LÄHME!', 'HAYDİ!', true, 'Sa ründad hunte!', 'Kurtlara saldırıyorsun!', 'n1_victory', 1),
('n1_c9', 'village_hero', 'n1_victory', 'Sa oled julge!', 'Çok cesursun!', true, 'Tüdruk naeratab.', 'Kız gülümsüyor.', 'n1_friend', 1),
('n1_c10', 'village_hero', 'n1_friend', 'Muidugi! Ma olen sinu sõber!', 'Tabii ki! Ben senin arkadaşınım!', true, 'Taru naeratab.', 'Taru gülümsüyor.', 'n1_gate2', 1),
('n1_c11', 'village_hero', 'n1_end', 'See on alles algus!', 'Bu daha başlangıç!', true, 'Seiklus jätkub...', 'Macera devam ediyor...', 'n1_complete', 1);

INSERT INTO story_node_minigame (story_id, node_id, game_type, target_words, completion_node_id) VALUES
('village_hero', 'n1_gate1', 'MemoryGame', ARRAY['poiss','unistus','tugev','kaitsja','treenida','nõrk','keha','jooksma','hüppama','ronima'], 'n1_attack'),
('village_hero', 'n1_gate2', 'MemoryGame', ARRAY['torm','hunt','ründama','karjuma','kaitsma','kiire','valmis','kangelane','sõber'], 'n1_end');


-- ============================================
-- Story 7: Müüride Taga (Attack on Titan-inspired) A2
-- Humanity lives behind walls, giants roam outside
-- ============================================
INSERT INTO adventure_stories (id, title, subtitle_tr, subtitle_en, genre_tr, genre_en, emoji, color, cefr_level, sort_order)
VALUES ('behind_walls', 'Müüride Taga', 'Duvarların ardında güvenli bir hayat... ya da öyle mi?', 'A safe life behind the walls... or is it?', 'Aksiyon/Korku', 'Action/Horror', '🏰', '#4A4A5A', 'A2', 7);

INSERT INTO story_nodes (id, story_id, stage, language_level, speaker, scene, text_ee, text_tr, sort_order) VALUES
('w1_city', 'behind_walls', 2, 'A2', 'Anlatici', 'city_day', 'Linn Müüri sees on rahulik. Inimesed töötavad, lapsed mängivad. Kõrged müürid kaitsevad neid juba sada aastat. Keegi ei mäleta, miks müürid ehitati. Keegi ei küsi.', 'Duvarın içindeki şehir huzurlu. İnsanlar çalışıyor, çocuklar oynuyor. Yüksek duvarlar onları yüz yıldır koruyor. Kimse duvarların neden inşa edildiğini hatırlamıyor. Kimse sormuyor.', 1),
('w1_eren', 'behind_walls', 2, 'A2', 'Kai', 'city_day', 'Mina tahan teada, mis on müüri taga! Miks me ei tohi välja minna? Miks keegi ei räägi sellest? Ma ei taha elada nagu lind puuris!', 'Duvarın arkasında ne olduğunu bilmek istiyorum! Neden dışarı çıkamıyoruz? Neden kimse bundan bahsetmiyor? Kafesteki kuş gibi yaşamak istemiyorum!', 2),
('w1_friend', 'behind_walls', 2, 'A2', 'Liina', 'city_day', 'Kai, ole vait! Kui valvurid kuulevad sind... Sa tead, mis juhtub nendega, kes küsivad liiga palju küsimusi. Nad kaovad.', 'Kai, sus! Muhafızlar duyarsa... Çok fazla soru soranların başına ne geldiğini biliyorsun. Ortadan kayboluyorlar.', 3),
('w1_quake', 'behind_walls', 2, 'A2', 'Anlatici', 'storm', 'Maa väriseb. Müürid pragisevad. Midagi SUURT liigub müüri taga. Vari langeb linnale — see on hiiglane! Kümne maja kõrgune olend, kellel on tühi pilk ja aeglased sammud.', 'Yer sarsılıyor. Duvarlar çatlıyor. Duvarın arkasında DEV bir şey hareket ediyor. Şehre bir gölge düşüyor — on ev yüksekliğinde, boş bakışlı ve yavaş adımlı bir varlık!', 4),
('w1_breach', 'behind_walls', 2, 'A2', 'Anlatici', 'storm', 'Müür puruneb! Hiiglane astub linna sisse! Inimesed karjuvad. Sõdurid jooksevad, aga nad on liiga väikesed. Nende mõõgad ei tee hiiglasele midagi.', 'Duvar kırılıyor! Dev şehre giriyor! İnsanlar çığlık atıyor. Askerler koşuyor ama çok küçükler. Kılıçları deve hiçbir şey yapmıyor.', 5),
('w1_gate1', 'behind_walls', 2, 'A2', 'Anlatici', 'storm', 'Enne edasi minekut — kontrolli sõnavara!', 'İlerlemeden önce — kelime bilgini kontrol et!', 6),
('w1_escape', 'behind_walls', 2, 'A2', 'Kai', 'bridge', 'Me peame põgenema! Liina, tule! Teisel pool jõge on teine müür — see on veel terve!', 'Kaçmalıyız! Liina, gel! Nehrin diğer tarafında başka bir duvar var — hâlâ sağlam!', 7),
('w1_decision', 'behind_walls', 2, 'A2', 'Kai', 'river', 'Ma tulen tagasi. Ma tahan saada sõduriks. Ma tahan võidelda hiiglaste vastu. Ma tahan teada tõde — mis on müüride taga ja MIKS nad tulevad.', 'Geri döneceğim. Asker olmak istiyorum. Devlere karşı savaşmak istiyorum. Gerçeği bilmek istiyorum — duvarların ardında ne var ve NEDEN geliyorlar.', 8),
('w1_gate2', 'behind_walls', 2, 'A2', 'Anlatici', 'river', 'Kontrolli oma teadmisi!', 'Bilgini kontrol et!', 9),
('w1_end', 'behind_walls', 2, 'A2', 'Kai', 'sunrise_clearing', 'Ma vannun — ma hävin kõik hiiglased. Iga. Viimase. Ühe.', 'Yemin ediyorum — tüm devleri yok edeceğim. Her. Birini. Tek tek.', 10),
('w1_complete', 'behind_walls', 2, 'A2', 'Anlatici', 'sunrise_clearing', 'Müüride Taga — 1. osa lõppenud. Kai teekond sõduriks alles algab. Mis on hiiglased? Kust nad tulevad?', 'Duvarların Ardında — Bölüm 1 tamamlandı. Kai''nin askerlik yolculuğu daha yeni başlıyor. Devler ne? Nereden geliyorlar?', 11);

INSERT INTO story_node_vocabulary (story_id, node_id, word, translation, context_hint, sort_order) VALUES
('behind_walls', 'w1_city', 'müür', 'duvar', 'Şehri çevreleyen yapı', 1),
('behind_walls', 'w1_city', 'rahulik', 'huzurlu', 'Sakin ve güvenli', 2),
('behind_walls', 'w1_city', 'töötama', 'çalışmak', 'İş yapmak', 3),
('behind_walls', 'w1_city', 'ehitati', 'inşa edildi', 'Yapıldı', 4),
('behind_walls', 'w1_eren', 'puur', 'kafes', 'İçinde hayvan tutulan', 1),
('behind_walls', 'w1_eren', 'välja', 'dışarı', 'İçerinin tersi', 2),
('behind_walls', 'w1_friend', 'valvur', 'muhafız', 'Koruyucu asker', 1),
('behind_walls', 'w1_friend', 'kaovad', 'kayboluyorlar', 'Ortadan yok oluyorlar', 2),
('behind_walls', 'w1_quake', 'väriseb', 'sarsılıyor', 'Titriyor', 1),
('behind_walls', 'w1_quake', 'hiiglane', 'dev', 'Çok büyük varlık', 2),
('behind_walls', 'w1_quake', 'vari', 'gölge', 'Işığı engelleyen', 3),
('behind_walls', 'w1_quake', 'olend', 'varlık/yaratık', 'Canlı bir şey', 4),
('behind_walls', 'w1_breach', 'puruneb', 'kırılıyor', 'Parçalanıyor', 1),
('behind_walls', 'w1_breach', 'sõdur', 'asker', 'Savaşan kişi', 2),
('behind_walls', 'w1_breach', 'mõõk', 'kılıç', 'Savaş silahı', 3),
('behind_walls', 'w1_escape', 'põgenema', 'kaçmak', 'Tehlikeden uzaklaşmak', 1),
('behind_walls', 'w1_escape', 'terve', 'sağlam', 'Kırılmamış', 2),
('behind_walls', 'w1_decision', 'võitlema', 'savaşmak', 'Mücadele etmek', 1),
('behind_walls', 'w1_decision', 'tõde', 'gerçek', 'Hakikat', 2),
('behind_walls', 'w1_end', 'vannuma', 'yemin etmek', 'Söz vermek', 1),
('behind_walls', 'w1_end', 'hävitama', 'yok etmek', 'Tamamen ortadan kaldırmak', 2);

INSERT INTO story_node_choices (id, story_id, node_id, text_ee, text_tr, is_correct_grammar, feedback_ee, feedback_tr, next_node_id, sort_order) VALUES
('w1_c1', 'behind_walls', 'w1_city', 'Mis on müüri taga?', 'Duvarın arkasında ne var?', true, 'Keegi ei tea. Keegi ei küsi.', 'Kimse bilmiyor. Kimse sormuyor.', 'w1_eren', 1),
('w1_c2', 'behind_walls', 'w1_eren', 'Ma nõustun Kaiga!', 'Kai''ye katılıyorum!', true, 'Aga keegi kuulab teid...', 'Ama birisi sizi dinliyor...', 'w1_friend', 1),
('w1_c3', 'behind_walls', 'w1_friend', 'Mis see heli on?', 'Bu ses ne?', true, 'Maa hakkab värisema...', 'Yer sarsılmaya başlıyor...', 'w1_quake', 1),
('w1_c4', 'behind_walls', 'w1_quake', 'See on... hiiglane?!', 'Bu... bir dev mi?!', true, 'Müür pragiseb...', 'Duvar çatlıyor...', 'w1_breach', 1),
('w1_c5', 'behind_walls', 'w1_breach', 'Me peame põgenema!', 'Kaçmalıyız!', true, 'Kai haarab Liina käest.', 'Kai, Liina''nın elinden tutuyor.', 'w1_gate1', 1),
('w1_c6', 'behind_walls', 'w1_escape', 'Jookse kiiremini!', 'Daha hızlı koş!', true, 'Te jõuate sillani.', 'Köprüye ulaşıyorsunuz.', 'w1_decision', 1),
('w1_c7', 'behind_walls', 'w1_decision', 'Ma võitlen!', 'Savaşacağım!', true, 'Su silmis on tuli.', 'Gözlerinde ateş var.', 'w1_gate2', 1),
('w1_c8', 'behind_walls', 'w1_end', 'Iga viimase ühe!', 'Her birini!', true, 'Kai teekond algab.', 'Kai''nin yolculuğu başlıyor.', 'w1_complete', 1);

INSERT INTO story_node_minigame (story_id, node_id, game_type, target_words, completion_node_id) VALUES
('behind_walls', 'w1_gate1', 'MemoryGame', ARRAY['müür','rahulik','töötama','puur','valvur','väriseb','hiiglane','sõdur','mõõk','puruneb'], 'w1_escape'),
('behind_walls', 'w1_gate2', 'MemoryGame', ARRAY['põgenema','terve','võitlema','tõde','vannuma','hävitama','vari','olend'], 'w1_end');


-- ============================================
-- Story 8: Mere Kuningas (One Piece-inspired) A1
-- Sailing Estonian waters seeking legendary treasure
-- ============================================
INSERT INTO adventure_stories (id, title, subtitle_tr, subtitle_en, genre_tr, genre_en, emoji, color, cefr_level, sort_order)
VALUES ('sea_king', 'Mere Kuningas', 'Efsanevi hazineyi bulmak için denize açıl', 'Set sail to find the legendary treasure', 'Macera', 'Adventure', '🏴‍☠️', '#2A6A9A', 'A1', 8);

INSERT INTO story_nodes (id, story_id, stage, language_level, speaker, scene, text_ee, text_tr, sort_order) VALUES
('s1_dream', 'sea_king', 1, 'A1', 'Anlatici', 'river', 'Väikeses sadamalinnas elab poiss nimega Ants. Ta vaatab iga päev merele ja unistab. Kuskil mere taga on aare — Mere Kuninga aare. Kes selle leiab, saab Mere Kuningaks.', 'Küçük bir liman kasabasında Ants adında bir çocuk yaşıyor. Her gün denize bakıp hayal kuruyor. Denizin ötesinde bir hazine var — Denizin Kralının Hazinesi. Onu bulan Denizin Kralı olur.', 1),
('s1_boat', 'sea_king', 1, 'A1', 'Ants', 'river', 'Ma vajan laeva! Mul ei ole raha, aga mul on käed ja puit. Ma ehitan ise laeva!', 'Bir gemiye ihtiyacım var! Param yok ama ellerim ve tahta var. Gemiyi kendim yapacağım!', 2),
('s1_build', 'sea_king', 1, 'A1', 'Anlatici', 'sunrise_clearing', 'Ants töötab iga päev. Ta lõikab puitu, lööb naelu ja õmbleb purje. Nädala pärast on tal väike, aga tugev paat.', 'Ants her gün çalışıyor. Tahta kesiyor, çivi çakıyor ve yelken dikiyor. Bir hafta sonra küçük ama sağlam bir teknesi var.', 3),
('s1_crew1', 'sea_king', 1, 'A1', 'Maali', 'city_day', 'Sa lähed merele? Üksi? Sa oled hull! ... Võta mind kaasa! Ma olen kokk — ma oskan süüa teha!', 'Denize mi çıkıyorsun? Yalnız mı? Delisin! ... Beni de al! Ben aşçıyım — yemek yapabilirim!', 4),
('s1_crew2', 'sea_king', 1, 'A1', 'Ott', 'city_day', 'Ma kuulsin, et sa otsid meeskonda. Ma olen tuuker — ma oskan sukelduda sügavale. Ja ma tean kaartide lugeda.', 'Mürettebat aradığını duydum. Ben dalgıcım — derine dalabilirim. Ve harita okumayı bilirim.', 5),
('s1_gate1', 'sea_king', 1, 'A1', 'Ants', 'river', 'Enne merele minekut — kas sa mäletad kõiki sõnu?', 'Denize çıkmadan önce — tüm kelimeleri hatırlıyor musun?', 6),
('s1_sail', 'sea_king', 1, 'A1', 'Anlatici', 'river', 'Kolm sõpra purjetavad merele! Tuul on tugev, lained on kõrged, aga nad laulavad ja naeravad. Seiklus on alanud!', 'Üç arkadaş denize yelken açıyor! Rüzgar güçlü, dalgalar yüksek ama şarkı söylüyorlar ve gülüyorlar. Macera başladı!', 6),
('s1_island', 'sea_king', 1, 'A1', 'Ott', 'forest_day', 'Maa! Ma näen saart! See ei ole kaardil... See on tundmatu saar!', 'Kara! Bir ada görüyorum! Haritada yok... Bilinmeyen bir ada!', 7),
('s1_explore_island', 'sea_king', 1, 'A1', 'Anlatici', 'forest_day', 'Saar on ilus ja roheline. Seal on puuvilju, värsket vett ja... vana kaart kivisse uuristatud! See näitab teed järgmise saare juurde.', 'Ada güzel ve yemyeşil. Meyveler, tatlı su ve... taşa oyulmuş eski bir harita var! Bir sonraki adaya giden yolu gösteriyor.', 8),
('s1_gate2', 'sea_king', 1, 'A1', 'Ants', 'forest_day', 'Enne edasi purjetamist — mäleta sõnu!', 'Yelken açmadan önce — kelimeleri hatırla!', 9),
('s1_end', 'sea_king', 1, 'A1', 'Ants', 'river', 'See on esimene jälg! Mere Kuninga aare ootab meid! Purjetame edasi, sõbrad!', 'Bu ilk ipucu! Denizin Kralının Hazinesi bizi bekliyor! Yelken açalım, arkadaşlar!', 10),
('s1_complete', 'sea_king', 1, 'A1', 'Anlatici', 'sunrise_clearing', 'Mere Kuningas — 1. osa lõppenud. Kolm sõpra on merel. Mis ootab neid järgmisel saarel?', 'Denizin Kralı — Bölüm 1 tamamlandı. Üç arkadaş denizde. Bir sonraki adada onları ne bekliyor?', 11);

INSERT INTO story_node_vocabulary (story_id, node_id, word, translation, context_hint, sort_order) VALUES
('sea_king', 's1_dream', 'sadam', 'liman', 'Gemilerin durduğu yer', 1),
('sea_king', 's1_dream', 'meri', 'deniz', 'Tuzlu su kütlesi', 2),
('sea_king', 's1_dream', 'aare', 'hazine', 'Değerli gizli şey', 3),
('sea_king', 's1_dream', 'kuningas', 'kral', 'Ülkenin hükümdarı', 4),
('sea_king', 's1_boat', 'laev', 'gemi', 'Denizde yüzen araç', 1),
('sea_king', 's1_boat', 'raha', 'para', 'Bir şey almak için kullanılan', 2),
('sea_king', 's1_boat', 'puit', 'tahta/odun', 'Ağaçtan yapılan malzeme', 3),
('sea_king', 's1_build', 'nael', 'çivi', 'Tahtayı birleştiren metal', 1),
('sea_king', 's1_build', 'puri', 'yelken', 'Rüzgarı yakalayan kumaş', 2),
('sea_king', 's1_build', 'paat', 'tekne', 'Küçük deniz aracı', 3),
('sea_king', 's1_crew1', 'kokk', 'aşçı', 'Yemek yapan kişi', 1),
('sea_king', 's1_crew1', 'süüa', 'yemek', 'Yediğin şey', 2),
('sea_king', 's1_crew2', 'meeskond', 'mürettebat', 'Gemideki ekip', 1),
('sea_king', 's1_crew2', 'sukelduma', 'dalmak', 'Suyun altına gitmek', 2),
('sea_king', 's1_sail', 'tuul', 'rüzgar', 'Esen hava akımı', 1),
('sea_king', 's1_sail', 'laine', 'dalga', 'Deniz yüzeyindeki hareket', 2),
('sea_king', 's1_island', 'saar', 'ada', 'Suyla çevrili kara parçası', 1),
('sea_king', 's1_explore_island', 'puuvili', 'meyve', 'Ağacın yenilebilir ürünü', 1);

INSERT INTO story_node_choices (id, story_id, node_id, text_ee, text_tr, is_correct_grammar, feedback_ee, feedback_tr, next_node_id, sort_order) VALUES
('s1_c1', 'sea_king', 's1_dream', 'Ma tahan saada Mere Kuningaks!', 'Denizin Kralı olmak istiyorum!', true, 'Ants silmad säravad.', 'Ants''un gözleri parlıyor.', 's1_boat', 1),
('s1_c2', 'sea_king', 's1_boat', 'Ma ehitan laeva!', 'Gemi yapacağım!', true, 'Töö algab!', 'İş başlıyor!', 's1_build', 1),
('s1_c3', 'sea_king', 's1_build', 'See on hea paat!', 'Bu güzel bir tekne!', true, 'Nüüd sa vajad meeskonda.', 'Şimdi mürettebata ihtiyacın var.', 's1_crew1', 1),
('s1_c4', 'sea_king', 's1_crew1', 'Tere tulemast meeskonda!', 'Mürettebata hoş geldin!', true, 'Maali hüppab rõõmust.', 'Maali sevinçten zıplıyor.', 's1_crew2', 1),
('s1_c5', 'sea_king', 's1_crew2', 'Meie meeskond on valmis!', 'Mürettebatımız hazır!', true, 'Kolm sõpra, üks unistus.', 'Üç arkadaş, bir hayal.', 's1_gate1', 1),
('s1_c6', 'sea_king', 's1_sail', 'Purjetame!', 'Yelken açalım!', true, 'Tuul viib teid merele.', 'Rüzgar sizi denize taşıyor.', 's1_island', 1),
('s1_c7', 'sea_king', 's1_island', 'Lähme uurima!', 'Keşfe çıkalım!', true, 'Te hüppate paadist välja.', 'Tekneden atlıyorsunuz.', 's1_explore_island', 1),
('s1_c8', 'sea_king', 's1_explore_island', 'See on kaart!', 'Bu bir harita!', true, 'Kaart näitab teed edasi.', 'Harita yolu gösteriyor.', 's1_gate2', 1),
('s1_c9', 'sea_king', 's1_end', 'Purjetame edasi!', 'Yelken açalım!', true, 'Seiklus jätkub!', 'Macera devam ediyor!', 's1_complete', 1);

INSERT INTO story_node_minigame (story_id, node_id, game_type, target_words, completion_node_id) VALUES
('sea_king', 's1_gate1', 'MemoryGame', ARRAY['sadam','meri','aare','kuningas','laev','raha','puit','nael','puri','paat'], 's1_sail'),
('sea_king', 's1_gate2', 'MemoryGame', ARRAY['kokk','meeskond','sukelduma','tuul','laine','saar','puuvili'], 's1_end');


-- ============================================
-- Story 9: Vaimude Maailm (Spirited Away-inspired) A2
-- Working in a bathhouse in the spirit world
-- ============================================
INSERT INTO adventure_stories (id, title, subtitle_tr, subtitle_en, genre_tr, genre_en, emoji, color, cefr_level, sort_order)
VALUES ('spirit_world', 'Vaimude Maailm', 'Ruhlar dünyasında hayatta kalmak', 'Surviving in the spirit world', 'Fantastik', 'Fantasy', '🏯', '#8A4A6A', 'A2', 9);

INSERT INTO story_nodes (id, story_id, stage, language_level, speaker, scene, text_ee, text_tr, sort_order) VALUES
('v1_tunnel', 'spirit_world', 2, 'A2', 'Anlatici', 'dark_awakening', 'Sa leiad vana tunneli metsas. Sa ei peaks sisse minema. Aga uudishimu on liiga tugev. Sa astud pimedasse.', 'Ormanda eski bir tünel buluyorsun. İçeri girmemelisin. Ama merak çok güçlü. Karanlığa adım atıyorsun.', 1),
('v1_other_side', 'spirit_world', 2, 'A2', 'Anlatici', 'city_day', 'Tunneli teises otsas on linn. Aga see linn ei ole normaalne — majad on tagurpidi, taevas on roheline ja tänavatel kõnnivad olendid, keda sa ei suuda kirjeldada.', 'Tünelin diğer ucunda bir şehir var. Ama bu şehir normal değil — evler ters, gökyüzü yeşil ve sokaklarda tarif edemeyeceğin varlıklar yürüyor.', 2),
('v1_no_return', 'spirit_world', 2, 'A2', 'Anlatici', 'dark_awakening', 'Sa tahad tagasi minna, aga tunnel on kadunud! Seinal on ainult kiri: "Kes siia tuleb, peab töötama. Kes ei tööta, muutub kiviks."', 'Geri dönmek istiyorsun ama tünel kaybolmuş! Duvarda sadece bir yazı: "Buraya gelen çalışmalı. Çalışmayan taşa dönüşür."', 3),
('v1_bathhouse', 'spirit_world', 2, 'A2', 'Anlatici', 'house_exterior', 'Linna keskel on hiigelsuur maja — vannituba vaimudele. Soe aur tõuseb katuselt. Väravas seisab vana naine.', 'Şehrin merkezinde devasa bir bina var — ruhlar için hamam. Çatıdan sıcak buhar yükseliyor. Kapıda yaşlı bir kadın duruyor.', 4),
('v1_witch', 'spirit_world', 2, 'A2', 'Vana naine', 'house_interior', 'Inimene! Huvitav... Sa tahad tagasi minna? Siis pead sa minu jaoks töötama. Pese põrandaid, küpseta leiba, teeninda kliente. Kui sa oled laisk, muutud KIVIKS.', 'İnsan! İlginç... Geri mi dönmek istiyorsun? O zaman benim için çalışmalısın. Yerleri sil, ekmek pişir, müşterilere hizmet et. Tembellik edersen TAŞA dönüşürsün.', 5),
('v1_gate1', 'spirit_world', 2, 'A2', 'Anlatici', 'house_interior', 'Kontrolli oma sõnavara!', 'Kelime bilgini kontrol et!', 6),
('v1_work', 'spirit_world', 2, 'A2', 'Anlatici', 'house_interior', 'Sa pesed, küpsetad ja teenindad. Kliendid on vaimud — mõned on lahked, mõned on kohutavad. Aga sa õpid nendega rääkima. Sa õpid nende keelt.', 'Siliyorsun, pişiriyorsun ve hizmet ediyorsun. Müşteriler ruhlar — bazıları nazik, bazıları korkunç. Ama onlarla konuşmayı öğreniyorsun. Onların dilini öğreniyorsun.', 7),
('v1_river_spirit', 'spirit_world', 2, 'A2', 'Jõevaim', 'river', 'Sa aitasid mind. Teised kohtlevad mind halvasti, sest ma olen must ja räpane. Aga SINA pesid mind puhtaks. Ma annan sulle kingituse — ühe mälestuse, mis aitab sind pääseda.', 'Bana yardım ettin. Diğerleri bana kötü davranıyor çünkü siyah ve kirli görünüyorum. Ama SEN beni temizledin. Sana bir hediye veriyorum — kaçmana yardım edecek bir anı.', 8),
('v1_gate2', 'spirit_world', 2, 'A2', 'Anlatici', 'house_interior', 'Enne lahkumist — mäleta sõnu!', 'Ayrılmadan önce — kelimeleri hatırla!', 9),
('v1_escape', 'spirit_world', 2, 'A2', 'Anlatici', 'sunrise_clearing', 'Jõevaim annab sulle kaardi. Tunnel ilmub taas! Sa jooksed. Vana naine karjub su järele. Aga sa oled juba tunnelisse jõudnud. Valgus paistab teises otsas.', 'Nehir ruhu sana haritayı veriyor. Tünel tekrar beliriyor! Koşuyorsun. Yaşlı kadın arkandan bağırıyor. Ama tünele çoktan ulaştın. Diğer uçta ışık parlıyor.', 10),
('v1_complete', 'spirit_world', 2, 'A2', 'Anlatici', 'forest_day', 'Vaimude Maailm — 1. osa lõppenud. Sa pääsesid! Aga jõevaim ütles midagi imelikku: "Me näeme veel."', 'Ruhlar Dünyası — Bölüm 1 tamamlandı. Kurtuldun! Ama nehir ruhu garip bir şey söyledi: "Yine görüşeceğiz."', 11);

INSERT INTO story_node_vocabulary (story_id, node_id, word, translation, context_hint, sort_order) VALUES
('spirit_world', 'v1_tunnel', 'uudishimu', 'merak', 'Bilmek isteme arzusu', 1),
('spirit_world', 'v1_other_side', 'tagurpidi', 'ters/baş aşağı', 'Normal yönün tersi', 1),
('spirit_world', 'v1_other_side', 'olendid', 'varlıklar', 'Yaşayan şeyler', 2),
('spirit_world', 'v1_no_return', 'muutub', 'dönüşüyor', 'Başka bir şey haline geliyor', 1),
('spirit_world', 'v1_no_return', 'kiviks', 'taşa', 'Taş haline', 2),
('spirit_world', 'v1_bathhouse', 'vannituba', 'hamam/banyo', 'Yıkanma yeri', 1),
('spirit_world', 'v1_bathhouse', 'aur', 'buhar', 'Sıcak suyun dumanı', 2),
('spirit_world', 'v1_witch', 'pesema', 'silmek/yıkamak', 'Temizlemek', 1),
('spirit_world', 'v1_witch', 'küpsetama', 'pişirmek', 'Fırında yapmak', 2),
('spirit_world', 'v1_witch', 'teenindama', 'hizmet etmek', 'Müşteriye yardım etmek', 3),
('spirit_world', 'v1_witch', 'laisk', 'tembel', 'Çalışmak istemeyen', 4),
('spirit_world', 'v1_work', 'lahke', 'nazik', 'Kibar ve iyi kalpli', 1),
('spirit_world', 'v1_work', 'kohutav', 'korkunç', 'Korku veren', 2),
('spirit_world', 'v1_work', 'keel', 'dil', 'İletişim aracı', 3),
('spirit_world', 'v1_river_spirit', 'räpane', 'kirli', 'Temiz olmayan', 1),
('spirit_world', 'v1_river_spirit', 'puhtaks', 'temiz hale', 'Kirsiz yapmak', 2),
('spirit_world', 'v1_river_spirit', 'kingitus', 'hediye', 'Birine verilen armağan', 3),
('spirit_world', 'v1_river_spirit', 'mälestus', 'anı/hatıra', 'Geçmişten kalan', 4),
('spirit_world', 'v1_escape', 'ilmub', 'beliriyor/ortaya çıkıyor', 'Görünür hale geliyor', 1);

INSERT INTO story_node_choices (id, story_id, node_id, text_ee, text_tr, is_correct_grammar, feedback_ee, feedback_tr, next_node_id, sort_order) VALUES
('v1_c1', 'spirit_world', 'v1_tunnel', 'Ma lähen sisse.', 'İçeri giriyorum.', true, 'Pimedus neelab sind.', 'Karanlık seni yutuyor.', 'v1_other_side', 1),
('v1_c2', 'spirit_world', 'v1_other_side', 'Kus ma olen?!', 'Ben neredeyim?!', true, 'See ei ole enam sinu maailm.', 'Burası artık senin dünyan değil.', 'v1_no_return', 1),
('v1_c3', 'spirit_world', 'v1_no_return', 'Ma pean tööd leidma!', 'İş bulmalıyım!', true, 'Sa näed suurt maja eemal.', 'Uzakta büyük bir bina görüyorsun.', 'v1_bathhouse', 1),
('v1_c4', 'spirit_world', 'v1_bathhouse', 'Kas te annate mulle tööd?', 'Bana iş verir misiniz?', true, 'Vana naine naeratab kavalalt.', 'Yaşlı kadın kurnazca gülümsüyor.', 'v1_witch', 1),
('v1_c5', 'spirit_world', 'v1_witch', 'Ma töötan! Ma ei ole laisk!', 'Çalışırım! Tembel değilim!', true, '"Vaatame," ütleb vana naine.', '"Göreceğiz," diyor yaşlı kadın.', 'v1_gate1', 1),
('v1_c6', 'spirit_world', 'v1_work', 'Ma õpin nende keelt!', 'Onların dilini öğreniyorum!', true, 'Vaimud hakkavad sind usaldama.', 'Ruhlar sana güvenmeye başlıyor.', 'v1_river_spirit', 1),
('v1_c7', 'spirit_world', 'v1_river_spirit', 'Aitäh, jõevaim!', 'Teşekkürler, nehir ruhu!', true, 'Jõevaim muutub kauniks valgusolendiks.', 'Nehir ruhu güzel bir ışık varlığına dönüşüyor.', 'v1_gate2', 1),
('v1_c8', 'spirit_world', 'v1_escape', 'Ma jooksen!', 'Koşuyorum!', true, 'Tunnel viib sind tagasi.', 'Tünel seni geri götürüyor.', 'v1_complete', 1);

INSERT INTO story_node_minigame (story_id, node_id, game_type, target_words, completion_node_id) VALUES
('spirit_world', 'v1_gate1', 'MemoryGame', ARRAY['uudishimu','tagurpidi','muutub','vannituba','aur','pesema','küpsetama','teenindama','laisk'], 'v1_work'),
('spirit_world', 'v1_gate2', 'MemoryGame', ARRAY['lahke','kohutav','keel','räpane','puhtaks','kingitus','mälestus','ilmub'], 'v1_escape');


-- ============================================
-- Story 10: Saatuse Raamat (Death Note-inspired) B1
-- Finding a mysterious book that shows truth
-- ============================================
INSERT INTO adventure_stories (id, title, subtitle_tr, subtitle_en, genre_tr, genre_en, emoji, color, cefr_level, sort_order)
VALUES ('fate_book', 'Saatuse Raamat', 'Kaderine hükmetme gücü — ama bedeli ne?', 'The power to command fate — but at what cost?', 'Psikolojik Gerilim', 'Psychological Thriller', '📓', '#2A2A3A', 'B1', 10);

INSERT INTO story_nodes (id, story_id, stage, language_level, speaker, scene, text_ee, text_tr, sort_order) VALUES
('d1_find', 'fate_book', 3, 'B1', 'Anlatici', 'city_day', 'Sa leiad raamatu tänavalt. See on must, ilma pealkirjata. Esimesel leheküljel on kiri: "Kes kirjutab siia nime ja soovi, see soov täitub. Aga iga soov nõuab hinda."', 'Sokakta bir kitap buluyorsun. Siyah, başlıksız. İlk sayfada bir yazı: "Buraya bir isim ve dilek yazan, dileği gerçekleşir. Ama her dileğin bir bedeli vardır."', 1),
('d1_test', 'fate_book', 3, 'B1', 'Anlatici', 'house_interior', 'Sa ei usu seda. Aga proovid: kirjutad oma naabri nime ja soovid, et ta oleks lahkem. Järgmisel päeval — naaber toob sulle koogi ja naeratab! See TOIMIB!', 'İnanmıyorsun. Ama deniyorsun: komşunun adını yazıyorsun ve daha nazik olmasını diliyorsun. Ertesi gün — komşu sana pasta getiriyor ve gülümsüyor! İŞE YARIYOR!', 2),
('d1_price', 'fate_book', 3, 'B1', 'Anlatici', 'dark_awakening', 'Aga siis märkad: raamatust on üks lehekülg kadunud. Ja sinu peegel... sinu nägu on veidi vanem. Iga soov võtab sinult aega. Päevi. Kuid. Aastaid?', 'Ama sonra fark ediyorsun: kitaptan bir sayfa kaybolmuş. Ve aynan... yüzün biraz daha yaşlı. Her dilek senden zaman alıyor. Günler. Aylar. Yıllar mı?', 3),
('d1_dilemma', 'fate_book', 3, 'B1', 'Anlatici', 'house_interior', 'Sa tead, et peaksid raamatu ära viskama. Aga siis kuuled uudiseid: sinu sõber on haige. Väga haige. Arstid ei saa aidata. Aga raamat saab.', 'Kitabı atman gerektiğini biliyorsun. Ama sonra haberleri duyuyorsun: arkadaşın hasta. Çok hasta. Doktorlar yardım edemiyor. Ama kitap edebilir.', 4),
('d1_gate1', 'fate_book', 3, 'B1', 'Anlatici', 'dark_awakening', 'Enne otsustamist — kontrolli oma teadmisi.', 'Karar vermeden önce — bilgini kontrol et.', 5),
('d1_choice', 'fate_book', 3, 'B1', 'Anlatici', 'house_interior', 'Sa kirjutad sõbra nime ja soovid talle tervist. Lehekülg põleb ära. Sa vaatad peeglisse — su nägu on viis aastat vanem. Aga sõber paraneb.', 'Arkadaşının adını yazıyorsun ve sağlık diliyorsun. Sayfa yanıp kül oluyor. Aynaya bakıyorsun — yüzün beş yıl daha yaşlı. Ama arkadaşın iyileşiyor.', 6),
('d1_stranger', 'fate_book', 3, 'B1', '???', 'dark_awakening', 'Huvitav valik. Sa ohverdasid oma aega teise inimese eest. Harvad teevad seda. Ma olen raamatu looja. Ja ma tahan seda tagasi.', 'İlginç bir seçim. Başka biri için zamanını feda ettin. Bunu yapan az kişi var. Ben kitabın yaratıcısıyım. Ve onu geri istiyorum.', 7),
('d1_gate2', 'fate_book', 3, 'B1', 'Anlatici', 'dark_awakening', 'Kontrolli sõnavara!', 'Kelime bilgini kontrol et!', 8),
('d1_end', 'fate_book', 3, 'B1', 'Anlatici', 'sunrise_clearing', 'Sa annad raamatu tagasi. Võõras kaob. Su peegel näitab taas su õiget nägu. Aga üks küsimus jääb: kas sa kasutaksid raamatut uuesti, kui saaksid?', 'Kitabı geri veriyorsun. Yabancı kayboluyor. Aynan tekrar gerçek yüzünü gösteriyor. Ama bir soru kalıyor: fırsat olsa kitabı tekrar kullanır mıydın?', 9),
('d1_complete', 'fate_book', 3, 'B1', 'Anlatici', 'sunrise_clearing', 'Saatuse Raamat — 1. osa lõppenud. Raamat on kadunud. Aga kas see on tõesti lõpp?', 'Kaderin Kitabı — Bölüm 1 tamamlandı. Kitap kayboldu. Ama bu gerçekten son mu?', 10);

INSERT INTO story_node_vocabulary (story_id, node_id, word, translation, context_hint, sort_order) VALUES
('fate_book', 'd1_find', 'pealkiri', 'başlık', 'Kitabın üstündeki isim', 1),
('fate_book', 'd1_find', 'soov', 'dilek', 'İstediğin şey', 2),
('fate_book', 'd1_find', 'täitub', 'gerçekleşiyor', 'Olması istenen oluyor', 3),
('fate_book', 'd1_find', 'hind', 'bedel/fiyat', 'Karşılığında ödenen', 4),
('fate_book', 'd1_test', 'naaber', 'komşu', 'Yanında yaşayan kişi', 1),
('fate_book', 'd1_test', 'toimib', 'işe yarıyor', 'Çalışıyor, etkili oluyor', 2),
('fate_book', 'd1_price', 'peegel', 'ayna', 'Yansımanı gösteren', 1),
('fate_book', 'd1_price', 'vanem', 'daha yaşlı', 'Daha ileri yaşta', 2),
('fate_book', 'd1_dilemma', 'haige', 'hasta', 'Sağlığı bozuk', 1),
('fate_book', 'd1_dilemma', 'arst', 'doktor', 'Tedavi eden kişi', 2),
('fate_book', 'd1_choice', 'tervis', 'sağlık', 'Vücudun iyi durumda olması', 1),
('fate_book', 'd1_choice', 'põleb', 'yanıyor', 'Ateşle yok oluyor', 2),
('fate_book', 'd1_choice', 'paraneb', 'iyileşiyor', 'Sağlığına kavuşuyor', 3),
('fate_book', 'd1_stranger', 'ohverdama', 'feda etmek', 'Değerli bir şeyden vazgeçmek', 1),
('fate_book', 'd1_stranger', 'looja', 'yaratıcı', 'Bir şeyi yapan/oluşturan', 2),
('fate_book', 'd1_end', 'võõras', 'yabancı', 'Tanımadığın kişi', 1),
('fate_book', 'd1_end', 'küsimus', 'soru', 'Cevaplanması gereken', 2);

INSERT INTO story_node_choices (id, story_id, node_id, text_ee, text_tr, is_correct_grammar, feedback_ee, feedback_tr, next_node_id, sort_order) VALUES
('d1_c1', 'fate_book', 'd1_find', 'Ma proovin seda!', 'Deneyeceğim!', true, 'Sa avad raamatu.', 'Kitabı açıyorsun.', 'd1_test', 1),
('d1_c2', 'fate_book', 'd1_test', 'See on uskumatu!', 'Bu inanılmaz!', true, 'Aga iga asi maksab...', 'Ama her şeyin bir bedeli var...', 'd1_price', 1),
('d1_c3', 'fate_book', 'd1_price', 'See on ohtlik!', 'Bu tehlikeli!', true, 'Jah, aga ahvatlev.', 'Evet, ama cezbedici.', 'd1_dilemma', 1),
('d1_c4', 'fate_book', 'd1_dilemma', 'Ma pean sõpra aitama!', 'Arkadaşıma yardım etmeliyim!', true, 'Sa avad raamatu.', 'Kitabı açıyorsun.', 'd1_gate1', 1),
('d1_c5', 'fate_book', 'd1_choice', 'See oli õige valik.', 'Bu doğru seçimdi.', true, 'Aga keegi vaatab sind...', 'Ama birisi seni izliyor...', 'd1_stranger', 1),
('d1_c6', 'fate_book', 'd1_stranger', 'Kes sa oled?', 'Sen kimsin?', true, 'Võõras naeratab.', 'Yabancı gülümsüyor.', 'd1_gate2', 1),
('d1_c7', 'fate_book', 'd1_end', 'Ma ei tea...', 'Bilmiyorum...', true, 'See küsimus jääb sinuga.', 'Bu soru seninle kalıyor.', 'd1_complete', 1);

INSERT INTO story_node_minigame (story_id, node_id, game_type, target_words, completion_node_id) VALUES
('fate_book', 'd1_gate1', 'MemoryGame', ARRAY['pealkiri','soov','täitub','hind','naaber','toimib','peegel','vanem','haige','arst'], 'd1_choice'),
('fate_book', 'd1_gate2', 'MemoryGame', ARRAY['tervis','põleb','paraneb','ohverdama','looja','võõras','küsimus'], 'd1_end');

-- Link vocabulary to existing words table
UPDATE story_node_vocabulary SET word_id = w.id
FROM words w WHERE story_node_vocabulary.word = w.estonian AND story_node_vocabulary.word_id IS NULL;
