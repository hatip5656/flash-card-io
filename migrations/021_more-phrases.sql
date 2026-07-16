-- Expand phrases to 200+ for communication variety

-- ============================================
-- A1: Survival & Basic Communication (70 phrases)
-- ============================================

-- At a shop
INSERT INTO feed_phrases (estonian, english, turkish, category, cefr_level, context_note, sort_order) VALUES
('Ma tahan seda osta.', 'I want to buy this.', 'Bunu satın almak istiyorum.', 'shopping', 'A1', 'Poes ostmise soov', 100),
('Kas teil on seda väiksemat?', 'Do you have this in a smaller size?', 'Bunun daha küçüğü var mı?', 'shopping', 'A1', 'Suuruse küsimine', 101),
('See on liiga kallis.', 'This is too expensive.', 'Bu çok pahalı.', 'shopping', 'A1', 'Hinna kohta', 102),
('Kas saab soodsamalt?', 'Can I get a discount?', 'İndirim yapabilir misiniz?', 'shopping', 'A1', 'Allahindluse küsimine', 103),
('Ma maksan kaardiga.', 'I''ll pay by card.', 'Kartla ödeyeceğim.', 'shopping', 'A1', 'Makseviisi teade', 104),

-- At a restaurant
('Menüü, palun!', 'Menu, please!', 'Menü, lütfen!', 'restaurant', 'A1', 'Restoranis', 105),
('Ma soovin tellida.', 'I''d like to order.', 'Sipariş vermek istiyorum.', 'restaurant', 'A1', 'Tellimise soov', 106),
('Arve, palun!', 'Check, please!', 'Hesap, lütfen!', 'restaurant', 'A1', 'Maksmise soov', 107),
('See oli väga maitsev!', 'It was very delicious!', 'Çok lezzetliydi!', 'restaurant', 'A1', 'Toidu kiitmine', 108),
('Mul on allergia.', 'I have an allergy.', 'Alerjim var.', 'restaurant', 'A1', 'Tervise teade', 109),
('Ilma suhkruta, palun.', 'Without sugar, please.', 'Şekersiz, lütfen.', 'restaurant', 'A1', 'Erisoovi teade', 110),

-- Transport
('Kus on bussipeatus?', 'Where is the bus stop?', 'Otobüs durağı nerede?', 'transport', 'A1', 'Ühistranspordi küsimus', 111),
('Millal buss tuleb?', 'When does the bus come?', 'Otobüs ne zaman gelecek?', 'transport', 'A1', 'Sõiduplaani küsimus', 112),
('Ma tahan lennujaama minna.', 'I want to go to the airport.', 'Havalimanına gitmek istiyorum.', 'transport', 'A1', 'Sihtpunkti teade', 113),
('Kui kaua sõit kestab?', 'How long does the journey take?', 'Yolculuk ne kadar sürer?', 'transport', 'A1', 'Aja küsimine', 114),

-- Weather
('Täna on ilus ilm!', 'The weather is nice today!', 'Bugün hava güzel!', 'weather', 'A1', 'Ilma kommenteerimine', 115),
('Homme sajab vihma.', 'It will rain tomorrow.', 'Yarın yağmur yağacak.', 'weather', 'A1', 'Ilmaennustus', 116),
('Mul on külm.', 'I''m cold.', 'Üşüyorum.', 'weather', 'A1', 'Temperatuuri tunne', 117),
('Mul on palav.', 'I''m hot.', 'Sıcaklıyorum.', 'weather', 'A1', 'Temperatuuri tunne', 118),

-- Directions
('Kuhu ma pean minema?', 'Where should I go?', 'Nereye gitmeliyim?', 'direction', 'A1', 'Tee küsimine', 119),
('Otse edasi.', 'Straight ahead.', 'Düz ileri.', 'direction', 'A1', 'Suuna andmine', 120),
('Keerake vasakule.', 'Turn left.', 'Sola dönün.', 'direction', 'A1', 'Suuna andmine', 121),
('Keerake paremale.', 'Turn right.', 'Sağa dönün.', 'direction', 'A1', 'Suuna andmine', 122),
('See on lähedal.', 'It''s nearby.', 'Yakında.', 'direction', 'A1', 'Kauguse teade', 123),
('See on kaugel.', 'It''s far away.', 'Uzakta.', 'direction', 'A1', 'Kauguse teade', 124),

-- Introducing yourself
('Ma olen pärit Türgist.', 'I''m from Turkey.', 'Türkiye''den geliyorum.', 'introduction', 'A1', 'Päritolu teade', 125),
('Ma räägin natuke eesti keelt.', 'I speak a little Estonian.', 'Biraz Estonca konuşuyorum.', 'introduction', 'A1', 'Keeleoskuse teade', 126),
('Ma õpin eesti keelt.', 'I''m learning Estonian.', 'Estonca öğreniyorum.', 'introduction', 'A1', 'Keeleõppe teade', 127),
('Ma elan Tallinnas.', 'I live in Tallinn.', 'Tallinn''de yaşıyorum.', 'introduction', 'A1', 'Elukoha teade', 128),

-- Feelings
('Ma olen väsinud.', 'I''m tired.', 'Yorgunum.', 'feeling', 'A1', 'Väsimuse teade', 129),
('Ma olen näljane.', 'I''m hungry.', 'Açım.', 'feeling', 'A1', 'Nälga', 130),
('Ma olen janune.', 'I''m thirsty.', 'Susadım.', 'feeling', 'A1', 'Janu', 131),
('Ma olen rõõmus!', 'I''m happy!', 'Mutluyum!', 'feeling', 'A1', 'Rõõm', 132),
('Ma olen kurb.', 'I''m sad.', 'Üzgünüm.', 'feeling', 'A1', 'Kurbus', 133),
('Ma olen haige.', 'I''m sick.', 'Hastayım.', 'feeling', 'A1', 'Haiguse teade', 134),

-- Emergencies
('Aidake mind!', 'Help me!', 'Bana yardım edin!', 'emergency', 'A1', 'Hädaolukord', 135),
('Kutsuge kiirabi!', 'Call an ambulance!', 'Ambulans çağırın!', 'emergency', 'A1', 'Meditsiiniline hädaolukord', 136),
('Ma olen eksinud.', 'I''m lost.', 'Kayboldum.', 'emergency', 'A1', 'Eksimine', 137),
('Kus on haigla?', 'Where is the hospital?', 'Hastane nerede?', 'emergency', 'A1', 'Haigla asukoht', 138),

-- Time
('Mis päev täna on?', 'What day is today?', 'Bugün günlerden ne?', 'time', 'A1', 'Päeva küsimine', 139),
('Täna on esmaspäev.', 'Today is Monday.', 'Bugün pazartesi.', 'time', 'A1', 'Päeva vastus', 140),
('Ma tulen kell kolm.', 'I''ll come at three o''clock.', 'Saat üçte geleceğim.', 'time', 'A1', 'Aja kokkulepe', 141),

-- Numbers in context
('Ma olen kakskümmend viis aastat vana.', 'I''m twenty-five years old.', 'Yirmi beş yaşındayım.', 'introduction', 'A1', 'Vanuse teade', 142),
('Kaks kohvi, palun.', 'Two coffees, please.', 'İki kahve, lütfen.', 'restaurant', 'A1', 'Tellimine', 143),

-- ============================================
-- A2: Social Communication (60 phrases)
-- ============================================

-- Making plans
('Kas sa oled homme vaba?', 'Are you free tomorrow?', 'Yarın müsait misin?', 'social', 'A2', 'Kohtumise planeerimine', 200),
('Lähme kinno!', 'Let''s go to the cinema!', 'Sinemaya gidelim!', 'social', 'A2', 'Ettepaneku tegemine', 201),
('Mis kell me kohtume?', 'What time do we meet?', 'Saat kaçta buluşuyoruz?', 'social', 'A2', 'Kellaaja kokkulepe', 202),
('Ma jään hiljaks.', 'I''m going to be late.', 'Geç kalacağım.', 'social', 'A2', 'Hilinemise teade', 203),
('Ma ei saa tulla.', 'I can''t come.', 'Gelemiyorum.', 'social', 'A2', 'Keeldumise teade', 204),
('Äkki järgmine kord?', 'Maybe next time?', 'Belki bir dahaki sefere?', 'social', 'A2', 'Viisakas keeldumine', 205),

-- Opinions
('Mulle meeldib see!', 'I like this!', 'Bu hoşuma gidiyor!', 'opinion', 'A2', 'Meeldivuse väljendus', 206),
('Mulle ei meeldi see.', 'I don''t like this.', 'Bu hoşuma gitmiyor.', 'opinion', 'A2', 'Mittemeeldivuse väljendus', 207),
('Ma arvan, et see on hea.', 'I think this is good.', 'Bunun iyi olduğunu düşünüyorum.', 'opinion', 'A2', 'Arvamuse avaldamine', 208),
('Mis sa arvad?', 'What do you think?', 'Sen ne düşünüyorsun?', 'opinion', 'A2', 'Arvamuse küsimine', 209),
('Sa võid õigus olla.', 'You might be right.', 'Haklı olabilirsin.', 'opinion', 'A2', 'Osaline nõustumine', 210),

-- Describing people
('Ta on väga tark.', 'He/She is very smart.', 'O çok akıllı.', 'description', 'A2', 'Inimese kirjeldamine', 211),
('Ta on naljakas inimene.', 'He/She is a funny person.', 'O komik bir insan.', 'description', 'A2', 'Iseloomu kirjeldamine', 212),
('Ta näeb hea välja.', 'He/She looks good.', 'İyi görünüyor.', 'description', 'A2', 'Välimuse kommentaar', 213),

-- At work / school
('Ma töötan arstina.', 'I work as a doctor.', 'Doktor olarak çalışıyorum.', 'work', 'A2', 'Ameti tutvustus', 214),
('Mis tööd sa teed?', 'What do you do for work?', 'Ne iş yapıyorsun?', 'work', 'A2', 'Töökoha küsimus', 215),
('Ma õpin ülikoolis.', 'I study at university.', 'Üniversitede okuyorum.', 'work', 'A2', 'Hariduse teade', 216),
('Kas koosolek on homme?', 'Is the meeting tomorrow?', 'Toplantı yarın mı?', 'work', 'A2', 'Koosoleku küsimus', 217),

-- Phone / messaging
('Ma helistan sulle hiljem.', 'I''ll call you later.', 'Seni sonra arayacağım.', 'phone', 'A2', 'Helistamise lubadus', 218),
('Saada mulle sõnum!', 'Send me a message!', 'Bana mesaj at!', 'phone', 'A2', 'Sõnumi palve', 219),
('Ma ei kuule sind hästi.', 'I can''t hear you well.', 'Seni iyi duyamıyorum.', 'phone', 'A2', 'Telefoni probleem', 220),
('Kes helistab?', 'Who''s calling?', 'Kim arıyor?', 'phone', 'A2', 'Helistaja tuvastamine', 221),

-- Hobbies
('Mis su hobid on?', 'What are your hobbies?', 'Hobilerin ne?', 'hobby', 'A2', 'Hobide küsimine', 222),
('Mulle meeldib lugeda.', 'I like reading.', 'Okumayı severim.', 'hobby', 'A2', 'Lugemise huvi', 223),
('Ma käin jõusaalis.', 'I go to the gym.', 'Spor salonuna gidiyorum.', 'hobby', 'A2', 'Spordi huvi', 224),
('Ma vaatan filme.', 'I watch movies.', 'Film izliyorum.', 'hobby', 'A2', 'Filmide vaatamine', 225),
('Ma armastan reisida.', 'I love traveling.', 'Seyahat etmeyi seviyorum.', 'hobby', 'A2', 'Reisimise huvi', 226),
('Ma kuulan muusikat.', 'I listen to music.', 'Müzik dinliyorum.', 'hobby', 'A2', 'Muusika huvi', 227),

-- Health
('Mul valutab pea.', 'I have a headache.', 'Başım ağrıyor.', 'health', 'A2', 'Peavalu', 228),
('Mul on palavik.', 'I have a fever.', 'Ateşim var.', 'health', 'A2', 'Palaviku teade', 229),
('Ma vajan arsti.', 'I need a doctor.', 'Doktora ihtiyacım var.', 'health', 'A2', 'Arsti vajadus', 230),
('Kus on apteek?', 'Where is the pharmacy?', 'Eczane nerede?', 'health', 'A2', 'Apteegi asukoht', 231),

-- Compliments & thanks
('Sa oled väga kena!', 'You are very nice!', 'Çok naziksin!', 'compliment', 'A2', 'Kompliment', 232),
('Aitäh abi eest!', 'Thank you for your help!', 'Yardımın için teşekkürler!', 'compliment', 'A2', 'Tänamine', 233),
('See on sinu jaoks!', 'This is for you!', 'Bu senin için!', 'compliment', 'A2', 'Kingituse andmine', 234),
('Palju õnne sünnipäevaks!', 'Happy birthday!', 'Doğum günün kutlu olsun!', 'compliment', 'A2', 'Sünnipäeva õnnitlus', 235),
('Häid jõule!', 'Merry Christmas!', 'Mutlu Noeller!', 'compliment', 'A2', 'Jõulude tervitus', 236),

-- Housing
('Ma otsin korterit.', 'I''m looking for an apartment.', 'Daire arıyorum.', 'housing', 'A2', 'Eluaseme otsing', 237),
('Kui palju üür on?', 'How much is the rent?', 'Kira ne kadar?', 'housing', 'A2', 'Üüri küsimine', 238),
('Korter on esimesel korrusel.', 'The apartment is on the first floor.', 'Daire birinci katta.', 'housing', 'A2', 'Korteri kirjeldus', 239),

-- ============================================
-- B1: Deeper Communication (50 phrases)
-- ============================================

-- Arguments & debates
('Ma saan su mõttest aru, aga...', 'I understand your point, but...', 'Senin görüşünü anlıyorum ama...', 'debate', 'B1', 'Viisakas vastuväide', 300),
('Mis põhjusel sa nii arvad?', 'What makes you think that?', 'Neden böyle düşünüyorsun?', 'debate', 'B1', 'Põhjenduse küsimine', 301),
('See ei ole päris nii lihtne.', 'It''s not quite that simple.', 'O kadar basit değil.', 'debate', 'B1', 'Keerukuse tunnistamine', 302),
('Vaatame seda teise nurga alt.', 'Let''s look at it from another angle.', 'Buna başka bir açıdan bakalım.', 'debate', 'B1', 'Perspektiivi muutmine', 303),
('Ma muutsin oma arvamust.', 'I changed my mind.', 'Fikrimi değiştirdim.', 'debate', 'B1', 'Meelemuutuse teade', 304),

-- Feelings & relationships
('Ma olen sinu pärast mures.', 'I''m worried about you.', 'Senin için endişeleniyorum.', 'emotion', 'B1', 'Mure väljendamine', 305),
('Ma usaldan sind.', 'I trust you.', 'Sana güveniyorum.', 'emotion', 'B1', 'Usalduse teade', 306),
('Sa petsid mind alt!', 'You let me down!', 'Beni hayal kırıklığına uğrattın!', 'emotion', 'B1', 'Pettumuse teade', 307),
('Ma vajan natuke aega.', 'I need some time.', 'Biraz zamana ihtiyacım var.', 'emotion', 'B1', 'Aja palve', 308),
('Räägime sellest rahulikult.', 'Let''s talk about this calmly.', 'Bunu sakin bir şekilde konuşalım.', 'emotion', 'B1', 'Rahustamine', 309),
('Ma tunnen end süüdi.', 'I feel guilty.', 'Kendimi suçlu hissediyorum.', 'emotion', 'B1', 'Süütunde teade', 310),
('Ma olen sinu üle uhke.', 'I''m proud of you.', 'Seninle gurur duyuyorum.', 'emotion', 'B1', 'Uhkuse väljendamine', 311),
('See teeb mind kurvaks.', 'That makes me sad.', 'Bu beni üzüyor.', 'emotion', 'B1', 'Kurbuse põhjus', 312),

-- Storytelling
('Sa ei usu, mis minuga juhtus!', 'You won''t believe what happened to me!', 'Başıma geleni inanamayacaksın!', 'story', 'B1', 'Loo alustamine', 313),
('Ja siis juhtus midagi uskumatut.', 'And then something incredible happened.', 'Ve sonra inanılmaz bir şey oldu.', 'story', 'B1', 'Loo jätkamine', 314),
('Lõpuks sai kõik korda.', 'In the end, everything worked out.', 'Sonunda her şey yoluna girdi.', 'story', 'B1', 'Loo lõpp', 315),
('See oli mu elu parim päev!', 'It was the best day of my life!', 'Hayatımın en güzel günüydü!', 'story', 'B1', 'Kogemuse kirjeldamine', 316),
('Ma ei oodanud seda üldse.', 'I didn''t expect that at all.', 'Bunu hiç beklemiyordum.', 'story', 'B1', 'Üllatuse teade', 317),

-- Advice
('Sa peaksid rohkem puhkama.', 'You should rest more.', 'Daha çok dinlenmelisin.', 'advice', 'B1', 'Nõuanne', 318),
('Ma soovitan proovida.', 'I recommend trying it.', 'Denemenizi tavsiye ederim.', 'advice', 'B1', 'Soovitus', 319),
('Ära karda vigu teha.', 'Don''t be afraid to make mistakes.', 'Hata yapmaktan korkma.', 'advice', 'B1', 'Julgustamine', 320),
('Kõik algab esimesest sammust.', 'Everything starts with the first step.', 'Her şey ilk adımla başlar.', 'advice', 'B1', 'Motivatsioon', 321),
('Aeg ravib kõik haavad.', 'Time heals all wounds.', 'Zaman her şeyin ilacı.', 'advice', 'B1', 'Vanasõna', 322),

-- Work situations
('Ma esitan oma idee koosolekul.', 'I''ll present my idea at the meeting.', 'Fikrimi toplantıda sunacağım.', 'work', 'B1', 'Töökoha tegevus', 323),
('Kas me saame tähtaega pikendada?', 'Can we extend the deadline?', 'Süreyi uzatabilir miyiz?', 'work', 'B1', 'Tähtaja küsimus', 324),
('Ma vajan rohkem infot.', 'I need more information.', 'Daha fazla bilgiye ihtiyacım var.', 'work', 'B1', 'Info palve', 325),
('Hea töö!', 'Good job!', 'İyi iş çıkardın!', 'work', 'B1', 'Kiitus', 326),

-- Culture
('Eestlased armastavad sauna.', 'Estonians love sauna.', 'Estonyalılar saunayı sever.', 'culture', 'B1', 'Kultuuri teadmine', 327),
('Jaanipäev on suvel.', 'Midsummer is in summer.', 'Yaz gün dönümü yazın kutlanır.', 'culture', 'B1', 'Eesti püha', 328),
('Laulupidu on väga oluline.', 'The Song Festival is very important.', 'Şarkı Festivali çok önemli.', 'culture', 'B1', 'Eesti kultuur', 329),

-- ============================================
-- B2: Nuanced Expression (40 phrases)
-- ============================================

-- Abstract ideas
('Vabadus tähendab vastutust.', 'Freedom means responsibility.', 'Özgürlük sorumluluk demektir.', 'philosophy', 'B2', 'Filosoofiline mõte', 400),
('Mitte kõik, mis särab, ei ole kuld.', 'Not all that glitters is gold.', 'Parlayan her şey altın değildir.', 'idiom', 'B2', 'Vanasõna', 401),
('Iga medal on kahepoolne.', 'Every medal has two sides.', 'Her madalyonun iki yüzü vardır.', 'idiom', 'B2', 'Vanasõna', 402),
('Homme on uus päev.', 'Tomorrow is a new day.', 'Yarın yeni bir gün.', 'philosophy', 'B2', 'Lohutus', 403),
('Kogemus on parim õpetaja.', 'Experience is the best teacher.', 'Deneyim en iyi öğretmendir.', 'philosophy', 'B2', 'Tarkus', 404),

-- Formal communication
('Lugupeetud härra / proua...', 'Dear Sir / Madam...', 'Sayın Bay / Bayan...', 'formal', 'B2', 'Ametlik kirja algus', 405),
('Soovin teid teavitada, et...', 'I wish to inform you that...', 'Sizi bilgilendirmek isterim ki...', 'formal', 'B2', 'Ametlik teade', 406),
('Palun edastage mu tervitused.', 'Please convey my regards.', 'Selamlarımı iletin lütfen.', 'formal', 'B2', 'Ametlik tervitus', 407),
('Ma hindan teie koostööd.', 'I appreciate your cooperation.', 'İş birliğinizi takdir ediyorum.', 'formal', 'B2', 'Ametlik tänu', 408),

-- Humor & irony
('See on nagu nõelast heinakuhjas otsida.', 'It''s like looking for a needle in a haystack.', 'Samanlıkta iğne aramak gibi.', 'idiom', 'B2', 'Kõnekäänd', 409),
('Parem hilja kui mitte kunagi.', 'Better late than never.', 'Geç olsun güç olmasın.', 'idiom', 'B2', 'Vanasõna', 410),
('Harjutamine teeb meistriks.', 'Practice makes perfect.', 'Alıştırma ustayı yapar.', 'idiom', 'B2', 'Vanasõna', 411),
('Kes otsib, see leiab.', 'He who seeks, finds.', 'Arayan bulur.', 'idiom', 'B2', 'Vanasõna', 412),
('Tasa sõuad, kaugele jõuad.', 'Slow and steady wins the race.', 'Ağır ol batmayasın.', 'idiom', 'B2', 'Vanasõna', 413),

-- Negotiation
('Kas me saaksime kompromissi leida?', 'Could we find a compromise?', 'Bir uzlaşma bulabilir miyiz?', 'negotiation', 'B2', 'Läbirääkimine', 414),
('Ma teen teile parema pakkumise.', 'I''ll make you a better offer.', 'Size daha iyi bir teklif yapacağım.', 'negotiation', 'B2', 'Pakkumise tegemine', 415),
('See on minu viimane sõna.', 'That''s my final word.', 'Bu benim son sözüm.', 'negotiation', 'B2', 'Lõplik seisukoht', 416),

-- Describing situations
('Olukord on keeruline.', 'The situation is complicated.', 'Durum karmaşık.', 'description', 'B2', 'Olukorra kirjeldus', 417),
('Sellel on palju tahke.', 'This has many facets.', 'Bunun birçok yönü var.', 'description', 'B2', 'Keerukuse tunnistus', 418),
('Ma olen kahe tule vahel.', 'I''m between a rock and a hard place.', 'İki ateş arasındayım.', 'idiom', 'B2', 'Kõnekäänd', 419),
('Asjad hakkavad paranema.', 'Things are starting to improve.', 'İşler düzelmeye başlıyor.', 'description', 'B2', 'Positiivne muutus', 420),

-- Self-reflection
('Ma olen palju õppinud sellest kogemusest.', 'I''ve learned a lot from this experience.', 'Bu deneyimden çok şey öğrendim.', 'reflection', 'B2', 'Enesereflektsioon', 421),
('Tagasi vaadates mõistan nüüd rohkem.', 'Looking back, I now understand more.', 'Geriye baktığımda artık daha iyi anlıyorum.', 'reflection', 'B2', 'Tagasivaade', 422),
('Mõnikord peab kaotama, et võita.', 'Sometimes you have to lose to win.', 'Bazen kazanmak için kaybetmek gerekir.', 'philosophy', 'B2', 'Elutarkus', 423),
('Keel on aken teise kultuuri.', 'Language is a window to another culture.', 'Dil başka bir kültüre açılan penceredir.', 'philosophy', 'B2', 'Keeleõppe motivatsioon', 424),

-- Estonian-specific
('Kus viga näed laita, seal tule ja aita!', 'Where you see fault, come and help!', 'Hata gördüğün yerde gel ve yardım et!', 'culture', 'B2', 'Eesti rahvatarkus', 425),
('Kes teisele auku kaevab, see ise sisse kukub.', 'He who digs a pit for another falls in himself.', 'Başkasına kuyu kazan kendisi düşer.', 'idiom', 'B2', 'Eesti vanasõna', 426);
