-- Dialog conversations for speaking practice

CREATE TABLE IF NOT EXISTS dialogs (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  title_tr TEXT NOT NULL,
  cefr_level TEXT NOT NULL,
  category TEXT NOT NULL,
  situation TEXT NOT NULL,
  situation_tr TEXT NOT NULL,
  icon TEXT NOT NULL DEFAULT '💬',
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_dialogs_level ON dialogs (cefr_level, sort_order);

CREATE TABLE IF NOT EXISTS dialog_lines (
  id SERIAL PRIMARY KEY,
  dialog_id TEXT NOT NULL REFERENCES dialogs(id) ON DELETE CASCADE,
  speaker TEXT NOT NULL,
  estonian TEXT NOT NULL,
  english TEXT NOT NULL DEFAULT '',
  turkish TEXT NOT NULL DEFAULT '',
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_dialog_lines_dialog ON dialog_lines (dialog_id, sort_order);

-- A1: Tutvumine (Meeting people)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('a1-meeting', 'Tutvumine', 'Tanışma', 'A1', 'everyday', 'You meet someone new at a café.', 'Kafede yeni biriyle tanışıyorsunuz.', '👋', 1);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('a1-meeting', 'A', 'Tere! Minu nimi on Anna. Mis sinu nimi on?', 'Hello! My name is Anna. What is your name?', 'Merhaba! Benim adım Anna. Senin adın ne?', 1),
('a1-meeting', 'B', 'Tere! Mina olen Murat. Väga meeldiv!', 'Hello! I am Murat. Very nice to meet you!', 'Merhaba! Ben Murat. Çok memnun oldum!', 2),
('a1-meeting', 'A', 'Kust sa pärit oled?', 'Where are you from?', 'Nerelisin?', 3),
('a1-meeting', 'B', 'Ma olen Türgist. Aga sina?', 'I am from Turkey. And you?', 'Ben Türkiye''den. Ya sen?', 4),
('a1-meeting', 'A', 'Ma olen Tallinnast. Mis sa teed Eestis?', 'I am from Tallinn. What do you do in Estonia?', 'Ben Tallinn''liyim. Estonya''da ne yapıyorsun?', 5),
('a1-meeting', 'B', 'Ma õpin eesti keelt. See on väga huvitav!', 'I study Estonian. It is very interesting!', 'Estonca öğreniyorum. Çok ilginç!', 6),
('a1-meeting', 'A', 'Kui kaua sa oled Eestis olnud?', 'How long have you been in Estonia?', 'Ne kadar zamandır Estonya''dasın?', 7),
('a1-meeting', 'B', 'Ma olen siin olnud kolm kuud.', 'I have been here for three months.', 'Üç aydır buradayım.', 8);

-- A1: Poes (At the shop)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('a1-shop', 'Poes', 'Markette', 'A1', 'everyday', 'You are buying food at a grocery store.', 'Bir markette yiyecek alıyorsunuz.', '🛒', 2);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('a1-shop', 'A', 'Tere! Kas ma saan teid aidata?', 'Hello! Can I help you?', 'Merhaba! Size yardımcı olabilir miyim?', 1),
('a1-shop', 'B', 'Tere! Jah, palun. Kus on piim?', 'Hello! Yes, please. Where is the milk?', 'Merhaba! Evet, lütfen. Süt nerede?', 2),
('a1-shop', 'A', 'Piim on seal, teises riiulis.', 'The milk is there, on the second shelf.', 'Süt orada, ikinci rafta.', 3),
('a1-shop', 'B', 'Aitäh! Kui palju see leib maksab?', 'Thanks! How much does this bread cost?', 'Teşekkürler! Bu ekmek ne kadar?', 4),
('a1-shop', 'A', 'See maksab kaks eurot.', 'It costs two euros.', 'İki euro.', 5),
('a1-shop', 'B', 'Hästi, ma võtan selle. Ja ühe piima ka.', 'Okay, I will take this one. And one milk too.', 'Tamam, bunu alayım. Bir de süt.', 6),
('a1-shop', 'A', 'Kokku neli eurot viiskümmend senti.', 'Total four euros fifty cents.', 'Toplam dört euro elli sent.', 7),
('a1-shop', 'B', 'Palun. Aitäh ja head aega!', 'Here you go. Thank you and goodbye!', 'Buyurun. Teşekkürler ve iyi günler!', 8);

-- A1: Restoranis (At the restaurant)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('a1-restaurant', 'Restoranis', 'Restoranda', 'A1', 'everyday', 'You are ordering food at a restaurant.', 'Bir restoranda yemek sipariş ediyorsunuz.', '🍽️', 3);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('a1-restaurant', 'A', 'Tere! Tere tulemast! Mitu inimest?', 'Hello! Welcome! How many people?', 'Merhaba! Hoş geldiniz! Kaç kişi?', 1),
('a1-restaurant', 'B', 'Tere! Meid on kaks.', 'Hello! There are two of us.', 'Merhaba! İki kişiyiz.', 2),
('a1-restaurant', 'A', 'Palun, istuge siia. Siin on menüü.', 'Please, sit here. Here is the menu.', 'Buyurun, buraya oturun. İşte menü.', 3),
('a1-restaurant', 'B', 'Aitäh! Mis te soovitate?', 'Thanks! What do you recommend?', 'Teşekkürler! Ne önerirsiniz?', 4),
('a1-restaurant', 'A', 'Meie supid on väga head. Eriti kartulipuljong.', 'Our soups are very good. Especially the potato soup.', 'Çorbalarımız çok iyi. Özellikle patates çorbası.', 5),
('a1-restaurant', 'B', 'Hästi, ma võtan ühe kartulipuljongi ja ühe salati.', 'Okay, I will have one potato soup and one salad.', 'Tamam, bir patates çorbası ve bir salata alayım.', 6),
('a1-restaurant', 'A', 'Ja mida te joote?', 'And what will you drink?', 'Ve ne içersiniz?', 7),
('a1-restaurant', 'B', 'Üks vesi, palun.', 'One water, please.', 'Bir su, lütfen.', 8);

-- A2: Arsti juures (At the doctor)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('a2-doctor', 'Arsti juures', 'Doktorda', 'A2', 'everyday', 'You visit a doctor because you are not feeling well.', 'Kendinizi iyi hissetmediğiniz için doktora gidiyorsunuz.', '🏥', 1);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('a2-doctor', 'A', 'Tere! Mis teid vaevab?', 'Hello! What is bothering you?', 'Merhaba! Şikayetiniz nedir?', 1),
('a2-doctor', 'B', 'Tere, doktor! Mul on peavalu ja kurk on valus.', 'Hello, doctor! I have a headache and my throat hurts.', 'Merhaba doktor! Başım ağrıyor ve boğazım ağrıyor.', 2),
('a2-doctor', 'A', 'Kui kaua see on kestnud?', 'How long has this been going on?', 'Bu ne zamandır sürüyor?', 3),
('a2-doctor', 'B', 'Kolm päeva juba. Ja mul on ka palavik.', 'Three days already. And I also have a fever.', 'Üç gündür. Ve ateşim de var.', 4),
('a2-doctor', 'A', 'Ma vaatan teie kurku. Palun avage suu. Jah, kurk on punane.', 'Let me check your throat. Please open your mouth. Yes, the throat is red.', 'Boğazınıza bakayım. Lütfen ağzınızı açın. Evet, boğaz kırmızı.', 5),
('a2-doctor', 'B', 'Kas see on tõsine?', 'Is it serious?', 'Ciddi mi?', 6),
('a2-doctor', 'A', 'Ei, ärge muretsege. Teil on gripp. Ma kirjutan teile rohtu.', 'No, don''t worry. You have the flu. I will prescribe medicine.', 'Hayır, endişelenmeyin. Gripsiniz. Size ilaç yazacağım.', 7),
('a2-doctor', 'B', 'Aitäh, doktor! Kui tihti ma pean rohtu võtma?', 'Thank you, doctor! How often should I take the medicine?', 'Teşekkürler doktor! İlacı ne sıklıkla almalıyım?', 8),
('a2-doctor', 'A', 'Kolm korda päevas, pärast sööki. Ja puhake palju!', 'Three times a day, after meals. And rest a lot!', 'Günde üç kez, yemeklerden sonra. Ve çok dinlenin!', 9);

-- A2: Tee küsimine (Asking directions)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('a2-directions', 'Tee küsimine', 'Yol Sorma', 'A2', 'everyday', 'You are lost and asking for directions on the street.', 'Kaybolmuşsunuz ve sokakta yol soruyorsunuz.', '🗺️', 2);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('a2-directions', 'B', 'Vabandage! Kas te saate mind aidata?', 'Excuse me! Can you help me?', 'Affedersiniz! Bana yardım edebilir misiniz?', 1),
('a2-directions', 'A', 'Muidugi! Mis teil vaja on?', 'Of course! What do you need?', 'Tabii ki! Neye ihtiyacınız var?', 2),
('a2-directions', 'B', 'Ma otsin raamatukogu. Kas te teate, kus see on?', 'I am looking for the library. Do you know where it is?', 'Kütüphaneyi arıyorum. Nerede olduğunu biliyor musunuz?', 3),
('a2-directions', 'A', 'Jah! Minge otse edasi kaks kvartalit, siis pöörake vasakule.', 'Yes! Go straight for two blocks, then turn left.', 'Evet! İki blok düz gidin, sonra sola dönün.', 4),
('a2-directions', 'B', 'Kas see on kaugel?', 'Is it far?', 'Uzak mı?', 5),
('a2-directions', 'A', 'Ei, see on umbes viie minuti kaugusel jalgsi.', 'No, it is about five minutes walk.', 'Hayır, yürüyerek yaklaşık beş dakika.', 6),
('a2-directions', 'B', 'Suurepärane! Suur aitäh teile!', 'Wonderful! Thank you very much!', 'Harika! Çok teşekkür ederim!', 7),
('a2-directions', 'A', 'Pole tänu väärt! Head teed!', 'You are welcome! Have a nice trip!', 'Rica ederim! İyi yolculuklar!', 8);

-- A2: Telefonivestlus (Phone call)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('a2-phone', 'Telefonivestlus', 'Telefon Görüşmesi', 'A2', 'everyday', 'You call a friend to make plans.', 'Bir arkadaşını plan yapmak için arıyorsun.', '📞', 3);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('a2-phone', 'A', 'Halloo?', 'Hello?', 'Alo?', 1),
('a2-phone', 'B', 'Tere, Liis! Siin on Murat. Kuidas läheb?', 'Hi, Liis! This is Murat. How are you?', 'Merhaba Liis! Ben Murat. Nasılsın?', 2),
('a2-phone', 'A', 'Tere, Murat! Mul läheb hästi, aitäh! Ja sinul?', 'Hi, Murat! I am fine, thanks! And you?', 'Merhaba Murat! İyiyim, teşekkürler! Ya sen?', 3),
('a2-phone', 'B', 'Tänan, hästi! Kuule, kas sa oled homme õhtul vaba?', 'Thanks, fine! Listen, are you free tomorrow evening?', 'Sağ ol, iyiyim! Dinle, yarın akşam müsait misin?', 4),
('a2-phone', 'A', 'Jah, miks?', 'Yes, why?', 'Evet, neden?', 5),
('a2-phone', 'B', 'Ma mõtlesin, et me võiksime kinos käia. Uus Eesti film tuleb.', 'I thought we could go to the cinema. A new Estonian movie is coming.', 'Sinemaya gidebiliriz diye düşündüm. Yeni bir Estonya filmi geliyor.', 6),
('a2-phone', 'A', 'See kõlab tore! Mis kell see algab?', 'That sounds nice! What time does it start?', 'Kulağa güzel geliyor! Saat kaçta başlıyor?', 7),
('a2-phone', 'B', 'Kell seitse. Kohtume kino ees kell kuus nelikümmend viis?', 'At seven. Shall we meet in front of the cinema at six forty-five?', 'Saat yedide. Sinemanın önünde altı kırk beşte buluşalım mı?', 8),
('a2-phone', 'A', 'Sobib! Näeme homme!', 'Sounds good! See you tomorrow!', 'Olur! Yarın görüşürüz!', 9);

-- B1: Töövestlus (Job interview)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('b1-job', 'Töövestlus', 'İş Görüşmesi', 'B1', 'social', 'You are at a job interview for a customer service position.', 'Müşteri hizmetleri pozisyonu için iş görüşmesindesiniz.', '💼', 1);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('b1-job', 'A', 'Tere! Palun istuge. Rääkige mulle oma varasemast töökogemusest.', 'Hello! Please sit down. Tell me about your previous work experience.', 'Merhaba! Lütfen oturun. Bana önceki iş deneyiminizden bahsedin.', 1),
('b1-job', 'B', 'Tere! Ma olen töötanud klienditeenindajana kaks aastat.', 'Hello! I have worked as a customer service agent for two years.', 'Merhaba! İki yıl müşteri hizmetleri temsilcisi olarak çalıştım.', 2),
('b1-job', 'A', 'Miks te soovite just meie firmas töötada?', 'Why do you want to work in our company specifically?', 'Neden özellikle bizim şirketimizde çalışmak istiyorsunuz?', 3),
('b1-job', 'B', 'Teie firma on tuntud hea töökeskkonna poolest ja ma tahan areneda.', 'Your company is known for a good work environment and I want to develop.', 'Şirketiniz iyi çalışma ortamıyla tanınıyor ve gelişmek istiyorum.', 4),
('b1-job', 'A', 'Milliseid keeli te räägite?', 'What languages do you speak?', 'Hangi dilleri konuşuyorsunuz?', 5),
('b1-job', 'B', 'Ma räägin türgi keelt, inglise keelt ja õpin eesti keelt. Mu eesti keel on B1 tasemel.', 'I speak Turkish, English, and I am learning Estonian. My Estonian is at B1 level.', 'Türkçe, İngilizce konuşuyorum ve Estonca öğreniyorum. Estoncam B1 seviyesinde.', 6),
('b1-job', 'A', 'Väga hea! Millal te saaksite tööle asuda?', 'Very good! When could you start working?', 'Çok iyi! Ne zaman işe başlayabilirsiniz?', 7),
('b1-job', 'B', 'Ma saan alustada kahe nädala pärast.', 'I can start in two weeks.', 'İki hafta sonra başlayabilirim.', 8);

-- B1: Üürikorteri otsimine (Renting an apartment)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('b1-apartment', 'Korteri üürimine', 'Daire Kiralama', 'B1', 'social', 'You are calling about an apartment you saw in an ad.', 'İlanda gördüğünüz bir daire hakkında arıyorsunuz.', '🏠', 2);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('b1-apartment', 'A', 'Halloo, siin on Marika. Kuidas saan aidata?', 'Hello, this is Marika. How can I help?', 'Alo, ben Marika. Nasıl yardımcı olabilirim?', 1),
('b1-apartment', 'B', 'Tere! Ma helistasin kuulutuse pärast. Kas kahetoaline korter on veel vaba?', 'Hello! I am calling about the ad. Is the two-room apartment still available?', 'Merhaba! İlan için arıyorum. İki odalı daire hâlâ müsait mi?', 2),
('b1-apartment', 'A', 'Jah, see on veel vaba. Üür on neljasada eurot kuus pluss kommunaalkulud.', 'Yes, it is still available. The rent is four hundred euros per month plus utilities.', 'Evet, hâlâ müsait. Kira aylık dört yüz euro artı faturalar.', 3),
('b1-apartment', 'B', 'Kas korter on möbleeritud?', 'Is the apartment furnished?', 'Daire mobilyalı mı?', 4),
('b1-apartment', 'A', 'Jah, seal on voodi, laud, toolid ja köögimööbel.', 'Yes, there is a bed, table, chairs and kitchen furniture.', 'Evet, yatak, masa, sandalyeler ve mutfak mobilyası var.', 5),
('b1-apartment', 'B', 'Kas lemmikloomi tohib pidada?', 'Are pets allowed?', 'Evcil hayvan beslemek serbest mi?', 6),
('b1-apartment', 'A', 'Kahjuks mitte. Kas te soovite korterit vaatama tulla?', 'Unfortunately not. Would you like to come see the apartment?', 'Maalesef hayır. Daireyi görmeye gelmek ister misiniz?', 7),
('b1-apartment', 'B', 'Jah, kindlasti! Kas homme kell kümme sobib?', 'Yes, certainly! Would tomorrow at ten o''clock work?', 'Evet, kesinlikle! Yarın saat on olur mu?', 8),
('b1-apartment', 'A', 'Jah, sobib. Aadress on Pärnu maantee kakskümmend kolm.', 'Yes, that works. The address is Pärnu maantee twenty-three.', 'Evet, olur. Adres Pärnu maantee yirmi üç.', 9);

-- B2: Kaebuse esitamine (Making a complaint)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('b2-complaint', 'Kaebuse esitamine', 'Şikayet Etme', 'B2', 'professional', 'You are complaining to a store about a defective product.', 'Bir mağazada arızalı bir ürün hakkında şikayet ediyorsunuz.', '📋', 1);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('b2-complaint', 'B', 'Tere! Ma ostsin siit eelmisel nädalal sülearvuti ja see ei tööta korralikult.', 'Hello! I bought a laptop here last week and it doesn''t work properly.', 'Merhaba! Geçen hafta buradan bir dizüstü bilgisayar aldım ve düzgün çalışmıyor.', 1),
('b2-complaint', 'A', 'Mul on kahju seda kuulda. Mis täpsemalt viga on?', 'I am sorry to hear that. What exactly is wrong?', 'Bunu duyduğuma üzüldüm. Tam olarak sorun nedir?', 2),
('b2-complaint', 'B', 'Ekraan vilgub pidevalt ja arvuti läheb väga kuumaks.', 'The screen flickers constantly and the computer gets very hot.', 'Ekran sürekli titriyor ve bilgisayar çok ısınıyor.', 3),
('b2-complaint', 'A', 'Kas teil on ostutšekk kaasas?', 'Do you have the receipt with you?', 'Fişiniz yanınızda mı?', 4),
('b2-complaint', 'B', 'Jah, siin see on. Ma soovin kas raha tagasi saada või uut arvutit.', 'Yes, here it is. I would like either a refund or a new computer.', 'Evet, işte burada. Ya para iadesi ya da yeni bir bilgisayar istiyorum.', 5),
('b2-complaint', 'A', 'Ma saan aru. Me saame teile pakkuda asenduse samasuguse mudeliga. Või saame saata selle remonti.', 'I understand. We can offer you a replacement with the same model. Or we can send it for repair.', 'Anlıyorum. Size aynı modelle değişim sunabiliriz. Ya da tamire gönderebiliriz.', 6),
('b2-complaint', 'B', 'Ma eelistan asendust, palun. Kui kaua see aega võtab?', 'I prefer a replacement, please. How long will it take?', 'Değişimi tercih ederim, lütfen. Ne kadar sürer?', 7),
('b2-complaint', 'A', 'Me saame selle teile kohe anda. Palun täitke see avaldus.', 'We can give it to you right away. Please fill in this form.', 'Hemen verebiliriz. Lütfen bu formu doldurun.', 8);

-- B2: Arvamuse avaldamine (Expressing opinions / debate)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('b2-opinions', 'Arvamuste vahetus', 'Fikir Alışverişi', 'B2', 'professional', 'You discuss the benefits of learning languages with a colleague.', 'Bir meslektaşınızla dil öğrenmenin faydalarını tartışıyorsunuz.', '🗣️', 2);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('b2-opinions', 'A', 'Ma arvan, et keelte õppimine on tänapäeval väga oluline. Mida sina arvad?', 'I think learning languages is very important nowadays. What do you think?', 'Bence günümüzde dil öğrenmek çok önemli. Sen ne düşünüyorsun?', 1),
('b2-opinions', 'B', 'Olen täiesti nõus. Mitmekeelsus avab palju uksi tööturul.', 'I completely agree. Multilingualism opens many doors in the job market.', 'Tamamen katılıyorum. Çok dillilik iş piyasasında birçok kapı açıyor.', 2),
('b2-opinions', 'A', 'Jah, aga mõned inimesed arvavad, et piisab ainult inglise keelest.', 'Yes, but some people think that English alone is enough.', 'Evet, ama bazı insanlar sadece İngilizcenin yeterli olduğunu düşünüyor.', 3),
('b2-opinions', 'B', 'Inglise keel on küll oluline, aga kohaliku keele oskus näitab austust kultuuri vastu.', 'English is important, but knowing the local language shows respect for the culture.', 'İngilizce önemli ama yerel dili bilmek kültüre saygı gösterir.', 4),
('b2-opinions', 'A', 'Täpselt! Ja kohalikus keeles suhtlemine aitab paremini integreeruda.', 'Exactly! And communicating in the local language helps to integrate better.', 'Kesinlikle! Ve yerel dilde iletişim kurmak daha iyi entegre olmaya yardımcı olur.', 5),
('b2-opinions', 'B', 'Minu kogemuse põhjal on eesti keele õppimine mind palju aidanud igapäevaelus.', 'Based on my experience, learning Estonian has helped me a lot in everyday life.', 'Benim deneyimime göre Estonca öğrenmek günlük hayatımda bana çok yardımcı oldu.', 6),
('b2-opinions', 'A', 'Millised olid sinu jaoks kõige raskemad kohad eesti keele õppimisel?', 'What were the most difficult parts of learning Estonian for you?', 'Estonca öğrenirken senin için en zor kısımlar neydi?', 7),
('b2-opinions', 'B', 'Kindlasti käänded! Aga harjutamisega läheb aina paremaks.', 'Definitely the cases! But with practice it keeps getting better.', 'Kesinlikle isim halleri! Ama pratik yaptıkça giderek iyileşiyor.', 8);
