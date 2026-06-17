-- More diverse, fun, real-life dialogs

-- A1: Kohvikus sõbraga (Coffee with a friend)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('a1-coffee', 'Kohvikus sõbraga', 'Kafede Arkadaşla', 'A1', 'everyday', 'You meet a friend at a café and chat about your day.', 'Kafede bir arkadaşınla buluşup günün hakkında sohbet ediyorsun.', '☕', 4);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('a1-coffee', 'A', 'Tere! Kena sind näha! Kuidas su päev oli?', 'Hi! Nice to see you! How was your day?', 'Merhaba! Seni görmek güzel! Günün nasıldı?', 1),
('a1-coffee', 'B', 'Tere! Päev oli pikk. Ma olin tööl hommikust õhtuni.', 'Hi! The day was long. I was at work from morning to evening.', 'Merhaba! Gün uzundu. Sabahtan akşama kadar işteyim.', 2),
('a1-coffee', 'A', 'Oh, see on raske! Mida sa juua tahad? Mina võtan cappuccino.', 'Oh, that is tough! What do you want to drink? I will have a cappuccino.', 'Ah, zor olmuş! Ne içmek istersin? Ben cappuccino alacağım.', 3),
('a1-coffee', 'B', 'Ma tahan ühte teed palun. Ja võib-olla üht kooki ka!', 'I want one tea please. And maybe a cake too!', 'Bir çay istiyorum lütfen. Ve belki bir de pasta!', 4),
('a1-coffee', 'A', 'Hea mõte! Kuule, kas sa kuulsid, et Mari sai uue töö?', 'Good idea! Listen, did you hear that Mari got a new job?', 'İyi fikir! Dinle, Mari''nin yeni bir iş bulduğunu duydun mu?', 5),
('a1-coffee', 'B', 'Tõesti? Kus ta nüüd töötab?', 'Really? Where does she work now?', 'Gerçekten mi? Şimdi nerede çalışıyor?', 6),
('a1-coffee', 'A', 'Ta töötab IT-firmas. Ta on väga õnnelik!', 'She works at an IT company. She is very happy!', 'Bir IT şirketinde çalışıyor. Çok mutluymuş!', 7),
('a1-coffee', 'B', 'See on tore! Me peame temaga koos õhtust sööma minema.', 'That is nice! We should go out for dinner with her.', 'Ne güzel! Onunla birlikte akşam yemeğine çıkmalıyız.', 8);

-- A2: Peol (At a party)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('a2-party', 'Peol', 'Partide', 'A2', 'everyday', 'You are at a party and your friend points out someone interesting.', 'Bir partidessin ve arkadaşın ilginç birini gösteriyor.', '🎉', 4);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('a2-party', 'A', 'Vaata, see tüdruk seal! Kas sa tead, kes ta on?', 'Look, that girl there! Do you know who she is?', 'Bak, oradaki kız! Kim olduğunu biliyor musun?', 1),
('a2-party', 'B', 'Jah, see on Liisa. Ta õpib Tartu ülikoolis.', 'Yes, that is Liisa. She studies at Tartu University.', 'Evet, o Liisa. Tartu Üniversitesinde okuyor.', 2),
('a2-party', 'A', 'Ta on väga ilus. Kas ta on vallaline?', 'She is very beautiful. Is she single?', 'Çok güzel. Bekar mı?', 3),
('a2-party', 'B', 'Ma arvan küll. Tahan sind tutvustada?', 'I think so. Want me to introduce you?', 'Sanırım öyle. Seni tanıştırmamı ister misin?', 4),
('a2-party', 'A', 'Oi, ma olen natuke häbelik... Aga okei, lähme!', 'Oh, I am a bit shy... But okay, let''s go!', 'Ay, biraz utangacım... Ama tamam, gidelim!', 5),
('a2-party', 'B', 'Liisa, tere! See on mu sõber Murat. Ta on Türgist.', 'Liisa, hi! This is my friend Murat. He is from Turkey.', 'Liisa, merhaba! Bu benim arkadaşım Murat. Türkiye''den.', 6),
('a2-party', 'A', 'Tere, Liisa! Väga meeldiv tutvuda!', 'Hi, Liisa! Very nice to meet you!', 'Merhaba Liisa! Tanıştığımıza çok memnun oldum!', 7),
('a2-party', 'B', 'Tere! Meeldiv! Kas te naudite pidu?', 'Hi! Nice to meet you! Are you enjoying the party?', 'Merhaba! Memnun oldum! Partiden keyif alıyor musunuz?', 8),
('a2-party', 'A', 'Jah, muusika on suurepärane! Kas sa tahad tantsida?', 'Yes, the music is great! Do you want to dance?', 'Evet, müzik harika! Dans etmek ister misin?', 9);

-- A2: Lapsepõlvest rääkimine (Talking about childhood)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('a2-childhood', 'Lapsepõlvest rääkimine', 'Çocukluk Anıları', 'A2', 'everyday', 'You and a friend share childhood memories over dinner.', 'Yemekte bir arkadaşınla çocukluk anılarını paylaşıyorsunuz.', '🧒', 5);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('a2-childhood', 'A', 'Kus sa üles kasvasid?', 'Where did you grow up?', 'Nerede büyüdün?', 1),
('a2-childhood', 'B', 'Ma kasvasin üles väikses külas. Me elasime vanaema kõrval.', 'I grew up in a small village. We lived next to grandmother.', 'Küçük bir köyde büyüdüm. Büyükannenin yanında yaşıyorduk.', 2),
('a2-childhood', 'A', 'See kõlab väga armas! Mida sa lapsena tegid?', 'That sounds very sweet! What did you do as a child?', 'Çok tatlı geliyor! Çocukken ne yapardın?', 3),
('a2-childhood', 'B', 'Me mängisime iga päev õues. Ronisime puudesse ja ujusime järves.', 'We played outside every day. We climbed trees and swam in the lake.', 'Her gün dışarıda oynardık. Ağaçlara tırmanır, gölde yüzerdik.', 4),
('a2-childhood', 'A', 'Vau! Minu lapsepõlv oli teistsugune. Ma kasvasin linnas.', 'Wow! My childhood was different. I grew up in the city.', 'Vay! Benim çocukluğum farklıydı. Şehirde büyüdüm.', 5),
('a2-childhood', 'B', 'Kas sa igatsed oma lapsepõlve?', 'Do you miss your childhood?', 'Çocukluğunu özlüyor musun?', 6),
('a2-childhood', 'A', 'Jah, vahel küll. Elu oli lihtsam. Polnud muresid!', 'Yes, sometimes. Life was simpler. No worries!', 'Evet, bazen. Hayat daha basitti. Dert yoktu!', 7),
('a2-childhood', 'B', 'Jah, see on tõsi! Aga nüüd saame vähemalt kohvi juua!', 'Yes, that is true! But now at least we can drink coffee!', 'Evet, doğru! Ama en azından şimdi kahve içebiliyoruz!', 8);

-- B1: Klatšimine (Gossiping)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('b1-gossip', 'Klatšimine', 'Dedikodu', 'B1', 'social', 'You and a colleague are gossiping about office drama during lunch break.', 'Öğle arasında bir meslektaşınla ofis dedikodularını konuşuyorsunuz.', '🤫', 3);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('b1-gossip', 'A', 'Kuule, kas sa kuulsid, mis eile kontoris juhtus?', 'Listen, did you hear what happened at the office yesterday?', 'Dinle, dün ofiste ne olduğunu duydun mu?', 1),
('b1-gossip', 'B', 'Ei! Mis juhtus? Räägi!', 'No! What happened? Tell me!', 'Hayır! Ne oldu? Anlat!', 2),
('b1-gossip', 'A', 'Peeter ja Kati vaidlesid koosolekul! Kõik kuulsid!', 'Peeter and Kati argued at the meeting! Everyone heard!', 'Peeter ve Kati toplantıda kavga ettiler! Herkes duydu!', 3),
('b1-gossip', 'B', 'Mida? Ma arvasin, et nad on sõbrad!', 'What? I thought they were friends!', 'Ne? Arkadaş olduklarını sanıyordum!', 4),
('b1-gossip', 'A', 'Nad olid, aga kuuldavasti Peeter sai edutamise, mida Kati tahtis.', 'They were, but apparently Peeter got the promotion that Kati wanted.', 'Öyleydiler, ama görünüşe göre Peeter, Kati''nin istediği terfiyi almış.', 5),
('b1-gossip', 'B', 'Oi oi! See selgitab palju. Kati oli terve nädala pahane.', 'Oh dear! That explains a lot. Kati was upset the whole week.', 'Eyvah! Bu çok şeyi açıklıyor. Kati bütün hafta sinirliydi.', 6),
('b1-gossip', 'A', 'Ja see pole kõik! Kuuldavasti tahab Kati teise firmasse üle minna.', 'And that is not all! Apparently Kati wants to move to another company.', 'Ve hepsi bu değil! Görünüşe göre Kati başka bir şirkete geçmek istiyormuş.', 7),
('b1-gossip', 'B', 'Tõsiselt? Aga ära kellelegi räägi, et ma sulle ütlesin!', 'Seriously? But don''t tell anyone I told you!', 'Ciddi mi? Ama kimseye benim söylediğimi söyleme!', 8),
('b1-gossip', 'A', 'Muidugi mitte! See jääb meie vahele!', 'Of course not! This stays between us!', 'Tabii ki hayır! Bu aramızda kalsın!', 9);

-- B1: Kohtinguplaanid (Dating plans)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('b1-dating', 'Kohtinguplaanid', 'Buluşma Planları', 'B1', 'social', 'You ask a friend for advice about a first date.', 'İlk buluşma hakkında arkadaşından tavsiye istiyorsun.', '💕', 4);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('b1-dating', 'A', 'Mul on homme esimene kohting! Ma olen nii närvis!', 'I have my first date tomorrow! I am so nervous!', 'Yarın ilk buluşmam var! Çok heyecanlıyım!', 1),
('b1-dating', 'B', 'Tõesti? Kellega? Räägi mulle kõik!', 'Really? With whom? Tell me everything!', 'Gerçekten mi? Kiminle? Bana her şeyi anlat!', 2),
('b1-dating', 'A', 'Ta nimi on Kadri. Me kohtusime keelekursusel.', 'Her name is Kadri. We met at a language course.', 'Adı Kadri. Dil kursunda tanıştık.', 3),
('b1-dating', 'B', 'Oh, see on romantiline! Kuhu te lähete?', 'Oh, that is romantic! Where are you going?', 'Ah, ne romantik! Nereye gidiyorsunuz?', 4),
('b1-dating', 'A', 'Ma ei tea! Mis sa soovitad? Restoran? Kino?', 'I don''t know! What do you recommend? Restaurant? Cinema?', 'Bilmiyorum! Ne önerirsin? Restoran? Sinema?', 5),
('b1-dating', 'B', 'Esimesel kohtingul on restoran parem. Seal saate rääkida.', 'For a first date, a restaurant is better. You can talk there.', 'İlk buluşma için restoran daha iyi. Orada konuşabilirsiniz.', 6),
('b1-dating', 'A', 'Hea mõte! Aga millest me räägime? Ma kardan, et muutun vaikseks.', 'Good idea! But what do we talk about? I''m afraid I''ll become quiet.', 'İyi fikir! Ama neden bahsederiz? Sessiz kalacağımdan korkuyorum.', 7),
('b1-dating', 'B', 'Küsi tema hobide ja reisimise kohta. Inimesed armastavad endast rääkida!', 'Ask about her hobbies and travel. People love talking about themselves!', 'Hobileri ve seyahatleri hakkında sor. İnsanlar kendileri hakkında konuşmayı sever!', 8),
('b1-dating', 'A', 'Aitäh! Sa oled parim sõber!', 'Thanks! You are the best friend!', 'Teşekkürler! En iyi arkadaşsın!', 9);

-- B1: Sõber on kurb (Friend is sad)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('b1-sad-friend', 'Sõber on kurb', 'Üzgün Arkadaş', 'B1', 'social', 'Your friend is having a tough time and you comfort them.', 'Arkadaşın zor günler geçiriyor ve onu teselli ediyorsun.', '🫂', 5);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('b1-sad-friend', 'A', 'Hei, kas sinuga on kõik korras? Sa näed kurb välja.', 'Hey, are you okay? You look sad.', 'Hey, iyi misin? Üzgün görünüyorsun.', 1),
('b1-sad-friend', 'B', 'Ausalt öeldes ei ole. Mu tüdruksõber jättis mind maha.', 'Honestly, no. My girlfriend broke up with me.', 'Açıkçası hayır. Kız arkadaşım benden ayrıldı.', 2),
('b1-sad-friend', 'A', 'Oh ei! Mul on nii kahju! Mis juhtus?', 'Oh no! I am so sorry! What happened?', 'Ah hayır! Çok üzüldüm! Ne oldu?', 3),
('b1-sad-friend', 'B', 'Ta ütles, et me oleme liiga erinevad. Ma ei saanud aru.', 'She said we are too different. I didn''t understand.', 'Çok farklıyız dedi. Anlamadım.', 4),
('b1-sad-friend', 'A', 'See on raske. Aga aeg parandab kõik. Usu mind.', 'That is tough. But time heals everything. Believe me.', 'Zor bir durum. Ama zaman her şeyi iyileştirir. İnan bana.', 5),
('b1-sad-friend', 'B', 'Ma tean, aga praegu on väga valus.', 'I know, but right now it hurts a lot.', 'Biliyorum ama şu an çok acı veriyor.', 6),
('b1-sad-friend', 'A', 'Tule, lähme õhtul kuskile välja. Sa ei pea üksi olema.', 'Come on, let''s go out tonight. You don''t have to be alone.', 'Hadi, bu akşam bir yere çıkalım. Yalnız olmak zorunda değilsin.', 7),
('b1-sad-friend', 'B', 'Aitäh, et sa mu eest hoolid. Sa oled tõeline sõber.', 'Thank you for caring about me. You are a true friend.', 'İlgilendiğin için teşekkürler. Gerçek bir dostsun.', 8);

-- B2: Elust ja unistustest (About life and dreams)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('b2-dreams', 'Elust ja unistustest', 'Hayat ve Hayaller', 'B2', 'professional', 'You have a deep conversation with a friend about life goals and dreams.', 'Bir arkadaşınla hayat hedefleri ve hayaller hakkında derin bir sohbet ediyorsunuz.', '✨', 3);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('b2-dreams', 'A', 'Kas sa oled kunagi mõelnud, kus sa tahaksid viie aasta pärast olla?', 'Have you ever thought about where you would like to be in five years?', 'Beş yıl sonra nerede olmak istediğini hiç düşündün mü?', 1),
('b2-dreams', 'B', 'Jah, tihti. Ma tahaksin oma ettevõtet alustada.', 'Yes, often. I would like to start my own company.', 'Evet, sık sık. Kendi şirketimi kurmak isterdim.', 2),
('b2-dreams', 'A', 'Tõesti? Mis valdkonnas?', 'Really? In what field?', 'Gerçekten mi? Hangi alanda?', 3),
('b2-dreams', 'B', 'Tehnoloogia ja haridus. Ma tahan aidata inimestel keeli õppida.', 'Technology and education. I want to help people learn languages.', 'Teknoloji ve eğitim. İnsanların dil öğrenmesine yardım etmek istiyorum.', 4),
('b2-dreams', 'A', 'See on suurepärane unistus! Mis sind sellest inspirerib?', 'That is a wonderful dream! What inspires you about it?', 'Harika bir hayal! Seni ne ilham veriyor?', 5),
('b2-dreams', 'B', 'Mu enda kogemus Eestis. Keeleõpe muutis mu elu täielikult.', 'My own experience in Estonia. Language learning completely changed my life.', 'Estonya''daki kendi deneyimim. Dil öğrenmek hayatımı tamamen değiştirdi.', 6),
('b2-dreams', 'A', 'Ma mõistan. Kas sa ei karda riskida?', 'I understand. Aren''t you afraid to take risks?', 'Anlıyorum. Risk almaktan korkmuyor musun?', 7),
('b2-dreams', 'B', 'Muidugi kardan! Aga ma kardan rohkem seda, et ma ei proovi kunagi.', 'Of course I''m afraid! But I''m more afraid of never trying.', 'Tabii ki korkuyorum! Ama hiç denemekten daha çok korkuyorum.', 8),
('b2-dreams', 'A', 'See on väga tark mõte. Ma toetan sind igal juhul!', 'That is a very wise thought. I support you no matter what!', 'Çok akıllıca bir düşünce. Seni her durumda destekliyorum!', 9);

-- A2: Ilma üle kurtmine (Complaining about weather)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('a2-weather', 'Ilma üle kurtmine', 'Hava Şikayeti', 'A2', 'everyday', 'You and a colleague complain about Estonian weather.', 'Bir meslektaşınla Estonya havası hakkında şikayet ediyorsunuz.', '🌧️', 6);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('a2-weather', 'A', 'Jälle sajab vihma! Ma olen sellest ilmast tüdinud!', 'It is raining again! I am tired of this weather!', 'Yine yağmur yağıyor! Bu havadan bıktım!', 1),
('a2-weather', 'B', 'Jah, terve nädal on olnud hall ja vihmane.', 'Yes, the whole week has been gray and rainy.', 'Evet, bütün hafta gri ve yağmurlu.', 2),
('a2-weather', 'A', 'Türgis on praegu kolmkümmend kraadi ja päike paistab!', 'In Turkey it is now thirty degrees and the sun is shining!', 'Türkiye''de şu an otuz derece ve güneş parlıyor!', 3),
('a2-weather', 'B', 'Ära räägi! Sa teed mind kadedaks! Millal suvi tuleb?', 'Don''t say that! You are making me jealous! When will summer come?', 'Söyleme! Kıskanıyorum! Yaz ne zaman gelecek?', 4),
('a2-weather', 'A', 'Eesti suvi kestab umbes kaks nädalat juulis!', 'Estonian summer lasts about two weeks in July!', 'Estonya yazı Temmuzda yaklaşık iki hafta sürüyor!', 5),
('a2-weather', 'B', 'Ha ha, see on kurb aga tõsi! Aga valged ööd on ilusad.', 'Ha ha, that is sad but true! But the white nights are beautiful.', 'Ha ha, üzücü ama doğru! Ama beyaz geceler güzel.', 6),
('a2-weather', 'A', 'Jah, see on tõsi. Ja talvel on lumi ka ilus.', 'Yes, that is true. And in winter the snow is also beautiful.', 'Evet, doğru. Ve kışın kar da güzel.', 7),
('a2-weather', 'B', 'Okei, äkki Eesti ilm pole NII halb!', 'Okay, maybe Estonian weather is not THAT bad!', 'Tamam, belki Estonya havası O KADAR kötü değil!', 8);

-- B1: Spordist rääkimine (Talking about sports)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('b1-sports', 'Spordist rääkimine', 'Spor Sohbeti', 'B1', 'social', 'You discuss sports and exercise habits with a gym buddy.', 'Spor salonu arkadaşınla spor ve egzersiz alışkanlıklarını konuşuyorsunuz.', '⚽', 6);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('b1-sports', 'A', 'Kas sa vaatasid eile jalgpallimängu?', 'Did you watch the football game yesterday?', 'Dün futbol maçını izledin mi?', 1),
('b1-sports', 'B', 'Jah! See oli uskumatu mäng! Eesti võitis kolm-kaks!', 'Yes! It was an incredible game! Estonia won three to two!', 'Evet! İnanılmaz bir maçtı! Estonya üç-iki kazandı!', 2),
('b1-sports', 'A', 'Ma tean! Ma hüppasin diivanilt üles, kui viimane värav löödi!', 'I know! I jumped off the sofa when the last goal was scored!', 'Biliyorum! Son gol atıldığında koltuktan fırladım!', 3),
('b1-sports', 'B', 'Kas sa mängid ka ise jalgpalli?', 'Do you also play football yourself?', 'Sen de futbol oynuyor musun?', 4),
('b1-sports', 'A', 'Jah, iga nädal kolmapäeviti. Tule meiega mängima!', 'Yes, every week on Wednesdays. Come play with us!', 'Evet, her hafta çarşambaları. Gel bizimle oyna!', 5),
('b1-sports', 'B', 'Ma ei ole eriti hea, aga miks mitte! Kus te mängite?', 'I am not very good, but why not! Where do you play?', 'Çok iyi değilim ama neden olmasın! Nerede oynuyorsunuz?', 6),
('b1-sports', 'A', 'Kadrioru pargis. Kell kuus. Too lihtsalt spordijalanõud kaasa!', 'In Kadriorg park. At six. Just bring sports shoes!', 'Kadriorg parkında. Saat altıda. Sadece spor ayakkabı getir!', 7),
('b1-sports', 'B', 'Tehtud! Näeme kolmapäeval!', 'Done! See you on Wednesday!', 'Tamam! Çarşamba görüşürüz!', 8);

-- B2: Kultuurišokk (Culture shock)
INSERT INTO dialogs (id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order) VALUES
('b2-culture', 'Kultuurišokk', 'Kültür Şoku', 'B2', 'professional', 'You discuss cultural differences between Estonia and Turkey.', 'Estonya ve Türkiye arasındaki kültürel farklılıkları tartışıyorsunuz.', '🌍', 4);

INSERT INTO dialog_lines (dialog_id, speaker, estonian, english, turkish, sort_order) VALUES
('b2-culture', 'A', 'Mis oli sinu jaoks kõige raskem, kui sa Eestisse kolisid?', 'What was the hardest thing for you when you moved to Estonia?', 'Estonya''ya taşındığında senin için en zor olan ne oldu?', 1),
('b2-culture', 'B', 'Kindlasti vaikus! Türgis räägivad kõik korraga, aga siin on inimesed väga vaiksed.', 'Definitely the silence! In Turkey everyone talks at the same time, but here people are very quiet.', 'Kesinlikle sessizlik! Türkiye''de herkes aynı anda konuşur ama burada insanlar çok sessiz.', 2),
('b2-culture', 'A', 'Jah, eestlased ei armasta small talki. See on meie kultuuris normaalne.', 'Yes, Estonians don''t like small talk. That is normal in our culture.', 'Evet, Estonyalılar kısa sohbeti sevmez. Bu bizim kültürümüzde normal.', 3),
('b2-culture', 'B', 'Ma õppisin seda hindama. Nüüd meeldib mulle, et siin on rahulik.', 'I learned to appreciate it. Now I like that it is peaceful here.', 'Bunu takdir etmeyi öğrendim. Şimdi buranın huzurlu olması hoşuma gidiyor.', 4),
('b2-culture', 'A', 'Aga kas miski tundus kummaline?', 'But was something strange?', 'Ama tuhaf gelen bir şey oldu mu?', 5),
('b2-culture', 'B', 'Jah! Et eestlased ei kutsu sind kohe koju külla! Türgis kutsuks sind esimesel kohtumisel!', 'Yes! That Estonians don''t invite you home right away! In Turkey they would invite you on the first meeting!', 'Evet! Estonyalıların seni hemen eve davet etmemesi! Türkiye''de ilk görüşmede davet ederler!', 6),
('b2-culture', 'A', 'Ha ha, jah! Me oleme aeglased usaldama, aga kui sa oled sõber, siis eluks ajaks!', 'Ha ha, yes! We are slow to trust, but once you are a friend, it is for life!', 'Ha ha, evet! Güvenmeye yavaşız ama bir kez arkadaş oldun mu ömür boyu!', 7),
('b2-culture', 'B', 'See on ilus! Ma armastan seda eestlaste juures.', 'That is beautiful! I love that about Estonians.', 'Bu çok güzel! Estonyalıların bu özelliğini seviyorum.', 8);
