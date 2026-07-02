-- Polish anime stories: fix scenes, enrich text, add atmosphere

-- ============================================
-- KÜLA KAITSJA (Naruto) — scene & text fixes
-- ============================================

-- n1_old_man meets Taru in the FOREST, not at a house
UPDATE story_nodes SET scene = 'forest_day',
  text_ee = 'Taru puhkab puujuurte vahel, kui keegi räägib talle selja tagant. Vana mees halli habemega ja rahuliku pilguga seisab tema ees. "Tere, poiss. Sa treenid siin iga päev, eks? Ma olen sind juba nädalaid jälginud. Sinus on tuld."',
  text_tr = 'Taru ağaç kökleri arasında dinlenirken arkasından biri konuşuyor. Gri sakallı, sakin bakışlı yaşlı bir adam karşısında duruyor. "Merhaba çocuk. Her gün burada antrenman yapıyorsun, değil mi? Seni haftalardır izliyorum. İçinde ateş var."'
WHERE story_id = 'village_hero' AND id = 'n1_old_man';

-- Enrich the training montage
UPDATE story_nodes SET
  text_ee = 'Kuud mööduvad. Kevad muutub suveks, suvi sügiseks. Taru keha muutub tugevamaks — ta jookseb mäest üles ilma hingeldamata. Ta lõhub kivi ühe löögiga. Aga küla lastele on ta endiselt "see imelik poiss". Iga õhtu vaatab Taru küla tulesid kaugelt ja vaikib.',
  text_tr = 'Aylar geçiyor. Bahar yaza, yaz sonbahara dönüyor. Taru''nun vücudu güçleniyor — nefes nefese kalmadan tepeye koşuyor. Bir yumrukla taşı kırıyor. Ama köy çocukları için hâlâ "o garip çocuk". Her akşam Taru uzaktan köyün ışıklarına bakıp susuyor.'
WHERE story_id = 'village_hero' AND id = 'n1_months';

-- Enrich the wolf attack — more dread
UPDATE story_nodes SET
  text_ee = 'Öösel ärkab Taru karjumise peale. Taevas on veripunane. Torm raputab puid ja tuul ulub nagu sada hunti. Aga see EI OLE tuul — metsast jooksevad välja päris hundid. Nende silmad helendavad pimedas kollaselt. Nad on suuremad kui tavalised hundid. Midagi on neid muutnud.',
  text_tr = 'Gece Taru çığlıklarla uyanıyor. Gökyüzü kan kırmızısı. Fırtına ağaçları sallıyor ve rüzgar yüz kurt gibi uluyuor. Ama bu rüzgar DEĞİL — ormandan gerçek kurtlar çıkıyor. Gözleri karanlıkta sarı parlıyor. Normal kurtlardan büyükler. Bir şey onları değiştirmiş.'
WHERE story_id = 'village_hero' AND id = 'n1_attack';

-- Make the fight scene more vivid
UPDATE story_nodes SET
  text_ee = 'Taru seisab hundide ees, üksi. Tuul sasib tema juukseid. Ta käed värisevad — aga mitte hirmust. Adrenaliinist. Vana mehe sõnad kõlavad tema peas: "Tugev ei ole see, kes ei karda. Tugev on see, kes kardab, aga seisab ikkagi." Taru tõstab rusikad.',
  text_tr = 'Taru kurtların önünde tek başına duruyor. Rüzgar saçlarını savuruyor. Elleri titriyor — ama korkudan değil. Adrenalinden. Yaşlı adamın sözleri kafasında yankılanıyor: "Güçlü olan korkmayan değildir. Güçlü olan korkan ama yine de durandır." Taru yumruklarını kaldırıyor.'
WHERE story_id = 'village_hero' AND id = 'n1_fight';

-- Richer victory scene
UPDATE story_nodes SET
  text_ee = 'Viimane hunt ulub ja põgeneb metsa. Vaikus. Taru seisab küla keskel, veriste kätega ja rebenenud riietega. Siis — üks inimene plaksutab. Siis teine. Siis kogu küla. Inimesed, kes kunagi ei vaadanud talle otsa, nüüd nutavad ja naeravad korraga.',
  text_tr = 'Son kurt uluyarak ormana kaçıyor. Sessizlik. Taru köyün ortasında duruyor, elleri kanla, kıyafetleri yırtık. Sonra — bir kişi alkışlıyor. Sonra bir başkası. Sonra tüm köy. Ona hiç bakmayan insanlar şimdi aynı anda ağlayıp gülüyorlar.'
WHERE story_id = 'village_hero' AND id = 'n1_victory';


-- ============================================
-- MÜÜRIDE TAGA (AoT) — scene & text fixes
-- ============================================

-- Richer opening — feeling of confinement
UPDATE story_nodes SET
  text_ee = 'Müüri sees on elu rahulik. Liiga rahulik. Inimesed töötavad, lapsed mängivad, turuplatsil müüakse kala ja leiba. Kõrged müürid — viiekümne meetri kõrgused hallid seinad — kaitsevad linna juba sada aastat. Keegi ei mäleta enam, miks need ehitati. Ja keegi ei julge küsida.',
  text_tr = 'Duvarın içinde hayat huzurlu. Fazla huzurlu. İnsanlar çalışıyor, çocuklar oynuyor, pazar yerinde balık ve ekmek satılıyor. Yüksek duvarlar — elli metre yüksekliğinde gri duvarlar — şehri yüz yıldır koruyor. Kimse artık neden inşa edildiklerini hatırlamıyor. Ve kimse sormaya cesaret edemiyor.'
WHERE story_id = 'behind_walls' AND id = 'w1_city';

-- The quake scene — pure terror
UPDATE story_nodes SET
  text_ee = 'Maa väriseb. Tassid kukuvad laualt. Lapsed hakkavad nutma. Müürid — need igavesed, purunematud müürid — pragisevad. Praod jooksevad üles nagu välgud. Ja siis langeb vari. Hiiglane. Kümne maja kõrgune olend ilma nahata — ainult lihased ja tühi, naeratav nägu. Ta tõstab rusika.',
  text_tr = 'Yer sarsılıyor. Fincanlar masadan düşüyor. Çocuklar ağlamaya başlıyor. Duvarlar — o sonsuz, kırılmaz duvarlar — çatlıyor. Çatlaklar şimşek gibi yukarı koşuyor. Ve sonra bir gölge düşüyor. Dev. On ev yüksekliğinde, ciltsiz bir varlık — sadece kaslar ve boş, sırıtan bir yüz. Yumruğunu kaldırıyor.'
WHERE story_id = 'behind_walls' AND id = 'w1_quake';

-- Fix decision scene — should be walled_city (looking back at destruction)
UPDATE story_nodes SET scene = 'walled_city',
  text_ee = 'Kai seisab teisel pool jõge ja vaatab tagasi. Müür on purustatud. Suits tõuseb linna kohalt. Ta kuuleb endiselt karjumist. Tema käed on rusikas. "Ma tulen tagasi. Ma saan sõduriks. Ma õpin, kuidas neid tappa. Ja ma saan teada TÕDE — miks nad tulevad ja mis on müüride taga."',
  text_tr = 'Kai nehrin diğer tarafında durup arkasına bakıyor. Duvar yıkılmış. Şehrin üzerinden duman yükseliyor. Hâlâ çığlıkları duyuyor. Elleri yumruk. "Geri döneceğim. Asker olacağım. Onları öldürmeyi öğreneceğim. Ve GERÇEĞİ öğreneceğim — neden geliyorlar ve duvarların ardında ne var."'
WHERE story_id = 'behind_walls' AND id = 'w1_decision';

-- Fix gate2 scene
UPDATE story_nodes SET scene = 'walled_city' WHERE story_id = 'behind_walls' AND id = 'w1_gate2';

-- Final vow — more weight
UPDATE story_nodes SET
  text_ee = 'Kai tõuseb püsti. Tema silmis on midagi uut — mitte hirm, mitte viha. Kindlus. "Ma vannun. Ma hävin iga viimase hiiglase. Iga. Viimase. Ühe. Ja siis ma lähen müürist VÄLJA. Sest ma ei taha elada puuris."',
  text_tr = 'Kai ayağa kalkıyor. Gözlerinde yeni bir şey var — korku değil, öfke değil. Kararlılık. "Yemin ediyorum. Her bir devi yok edeceğim. Her. Birini. Tek tek. Ve sonra duvarın DIŞINA çıkacağım. Çünkü kafeste yaşamak istemiyorum."'
WHERE story_id = 'behind_walls' AND id = 'w1_end';


-- ============================================
-- MERE KUNINGAS (One Piece) — scene & text fixes
-- ============================================

-- Dream scene — more longing
UPDATE story_nodes SET
  text_ee = 'Ants istub sadama äärel iga päev. Tema jalad ripuvad vee kohal ja silmad on kinni. Meri lõhnab soola ja seikluse järele. Kuskil seal, lainete taga, on Mere Kuninga aare — legendaarne varandus, mida keegi ei ole leidnud. Kes selle leiab, saab Mere Kuningaks. Ja Ants teab: see kuningas on tema.',
  text_tr = 'Ants her gün limanın kenarında oturuyor. Ayakları suyun üzerinde sallanıyor, gözleri kapalı. Deniz tuz ve macera kokuyor. Bir yerlerde, dalgaların ardında, Denizin Kralının Hazinesi var — kimsenin bulamadığı efsanevi bir servet. Onu bulan Denizin Kralı olur. Ve Ants biliyor: o kral kendisi.'
WHERE story_id = 'sea_king' AND id = 's1_dream';

-- Building the boat — more tactile
UPDATE story_nodes SET
  text_ee = 'Ants töötab päevast päeva. Ta lõhub puitu kirvega — PRAHH! Ta lööb naelu haamriga — KLÕNKS! Ta õmbleb purje nõelaga, kuni sõrmed valutavad. Nädala pärast seisab rannal väike, aga uhke paat. Ants nimetab selle "Lootus".',
  text_tr = 'Ants günden güne çalışıyor. Baltayla tahta yarıyor — ÇRAAAK! Çekiçle çivi çakıyor — TINK! İğneyle yelken dikiyor, parmakları ağrıyana kadar. Bir hafta sonra sahilde küçük ama gururlu bir tekne duruyor. Ants ona "Umut" adını veriyor.'
WHERE story_id = 'sea_king' AND id = 's1_build';

-- Sailing scene — joy and freedom
UPDATE story_nodes SET
  text_ee = 'Puri täitub tuulega ja "Lootus" lendab üle lainete! Maali laulab köögis, Ott loeb kaarte ja Ants seisab paadi ninas, käed laiali. Esimest korda tema elus — ta on vaba. Meri on lõputu ja tulevik on lahtine.',
  text_tr = 'Yelken rüzgarla doluyor ve "Umut" dalgaların üzerinde uçuyor! Maali mutfakta şarkı söylüyor, Ott harita okuyor ve Ants teknenin burnunda, kolları açık duruyor. Hayatında ilk kez — özgür. Deniz sonsuz ve gelecek açık.'
WHERE story_id = 'sea_king' AND id = 's1_sail';

-- Island discovery — wonder
UPDATE story_nodes SET
  text_ee = 'Saar ilmub udust nagu unenägu. Valge liivarand, türkiissinne vesi ja kõrged palmipuud. Linnud, keda keegi ei ole kunagi näinud, lendavad üle nende peade. Ott vaatab kaarti. "Seda saart ei ole ühegi kaardi peal. Me oleme esimesed, kes siia jõuavad."',
  text_tr = 'Ada sisten bir rüya gibi beliriyor. Beyaz kumlu sahil, turkuaz su ve yüksek palmiye ağaçları. Kimsenin görmediği kuşlar başlarının üzerinde uçuyor. Ott haritaya bakıyor. "Bu ada hiçbir haritada yok. Buraya ulaşan ilk kişileriz."'
WHERE story_id = 'sea_king' AND id = 's1_island';


-- ============================================
-- VAIMUDE MAAILM (Spirited Away) — scene & text
-- ============================================

-- Tunnel entry — dread and curiosity
UPDATE story_nodes SET
  text_ee = 'Vana tunnel metsa süvikus. Sammal kasvab telliskividel ja seest puhub soe tuul — nagu keegi hingaks. Sa tead, et ei peaks sisse minema. Iga rakk sinu kehas ütleb "MINE TAGASI." Aga jalad liiguvad edasi, samm-sammult, pimeduse sisse.',
  text_tr = 'Ormanın derinliklerinde eski bir tünel. Tuğlaların üzerinde yosun büyüyor ve içeriden sıcak bir rüzgar esiyor — sanki biri nefes alıyor. İçeri girmememen gerektiğini biliyorsun. Vücudundaki her hücre "GERİ DÖN" diyor. Ama ayakların adım adım karanlığa ilerliyor.'
WHERE story_id = 'spirit_world' AND id = 'v1_tunnel';

-- Spirit town — pure wonder/horror
UPDATE story_nodes SET
  text_ee = 'Tunneli teises otsas avaneb linn, mida ei saa olla olemas. Majad kasvavad tagurpidi laest. Tänavad on tehtud veest, mille peal saab kõndida. Taevas on roheline ja täis silmi — tähted, mis pilgutavad. Olendid liiguvad igal pool: läbipaistvad kalad õhus, rääkivad kivid, tulest tehtud kassid.',
  text_tr = 'Tünelin diğer ucunda var olmaması gereken bir şehir açılıyor. Evler tavandan ters büyüyor. Sokaklar üzerinde yürünebilen sudan yapılmış. Gökyüzü yeşil ve gözlerle dolu — kırpışan yıldızlar. Her yerde varlıklar hareket ediyor: havada saydam balıklar, konuşan taşlar, ateşten kediler.'
WHERE story_id = 'spirit_world' AND id = 'v1_other_side';

-- The witch — menacing
UPDATE story_nodes SET
  text_ee = 'Vana naine on kolme meetri kõrgune, tema nina on terav ja silmad on kollased nagu kassil. Ta naeratab, aga see naeratus ei jõua silmadeni. "Inimene minu majas? Huvitav... See juhtub nii harva." Ta nipsutab sõrmi ja uks tema taga sulgub PAUGU-ga. "Sa tahad tagasi? Tööta. Pese, küpseta, teeninda. Ja KUNAGI ära ütle mulle oma nime. Kes ütleb oma nime, kaotab selle igaveseks."',
  text_tr = 'Yaşlı kadın üç metre boyunda, burnu sivri ve gözleri kedi gibi sarı. Gülümsüyor ama gülümseme gözlerine ulaşmıyor. "Evimde bir insan mı? İlginç... Bu çok nadiren olur." Parmaklarını şıklatıyor ve arkasındaki kapı ÇARPARAK kapanıyor. "Geri mi dönmek istiyorsun? Çalış. Sil, pişir, hizmet et. Ve ASLA bana adını söyleme. Adını söyleyen onu sonsuza dek kaybeder."'
WHERE story_id = 'spirit_world' AND id = 'v1_witch';

-- Escape — fix scene to spirit_town (running through magical streets)
UPDATE story_nodes SET scene = 'spirit_town',
  text_ee = 'Jõevaim annab sulle hõbedase peegli. Selles peeglis näed sa tunneli ust! Sa jooksed läbi vaimude linna — mööda veest tänavaid, üle tagurpidi majadest, läbi tulekatside karjade vahelt. Vana naine karjub su järele: "SA EI SAA PÕGENEDA! SINU NIMI KUULUB MULLE!" Aga sa jooksed. Tunnel on seal. Valgus paistab.',
  text_tr = 'Nehir ruhu sana gümüş bir ayna veriyor. Bu aynada tünel kapısını görüyorsun! Ruhlar şehrinde koşuyorsun — su sokaklardan geçerek, ters evlerin üzerinden, ateş kedi sürülerinin arasından. Yaşlı kadın arkandan bağırıyor: "KAÇAMAZSIN! ADIN BANA AİT!" Ama koşuyorsun. Tünel orada. Işık parlıyor.'
WHERE story_id = 'spirit_world' AND id = 'v1_escape';


-- ============================================
-- SAATUSE RAAMAT (Death Note) — scene & text
-- ============================================

-- Finding the book — atmospheric
UPDATE story_nodes SET
  text_ee = 'Vihm sajab. Sa kõnnid koju, pea maas. Siis sa näed seda — must raamat kõnniteel, veelombis. Mitte keegi teine ei märka seda. Sa tõstad selle üles. Kaaned on pehmed nagu nahk, aga külmad nagu jää. Sees on ainult üks lause: "Kirjuta nimi. Kirjuta soov. Maksa hind."',
  text_tr = 'Yağmur yağıyor. Eve yürüyorsun, başın önde. Sonra onu görüyorsun — kaldırımda bir su birikintisinde siyah bir kitap. Başka kimse fark etmiyor. Yerden alıyorsun. Kapakları deri gibi yumuşak ama buz gibi soğuk. İçinde tek bir cümle: "Bir isim yaz. Bir dilek yaz. Bedeli öde."'
WHERE story_id = 'fate_book' AND id = 'd1_find';

-- Testing — thrill and unease
UPDATE story_nodes SET
  text_ee = 'Kodus, üksi. Küünal põleb laual. Sa avad raamatu ja kirjutad väriseva käega: naabri nimi ja soov — "Olgu ta lahkem." Sa sulged raamatu ja naerad enda üle. "See on ju lihtsalt raamat." Aga järgmisel hommikul koputab naaber su uksele KOOGIGA. Ta, kes pole sinuga kolm aastat rääkinud. Ta NAERATAB.',
  text_tr = 'Evde, yalnız. Masada bir mum yanıyor. Kitabı açıyorsun ve titreyen elinle yazıyorsun: komşunun adı ve dilek — "Daha nazik olsun." Kitabı kapatıp kendi kendine gülüyorsun. "Bu sadece bir kitap." Ama ertesi sabah komşu kapını PASTAYLA çalıyor. Üç yıldır seninle konuşmayan adam. GÜLÜMSÜYOR.'
WHERE story_id = 'fate_book' AND id = 'd1_test';

-- The price revealed — horror
UPDATE story_nodes SET
  text_ee = 'Kolm päeva hiljem sa pesed hambaid ja näed seda peegli: üks hall juuksekarv. Sa oled kahekümne aastane — hallid juuksed ei peaks olema. Sa vaatad raamatut. Üks lehekülg on kadunud — see, kuhu sa kirjutasid. Ja äkki sa mõistad: raamat ei anna midagi tasuta. Iga soov VÕTAB sinult aega. Päevi. Kuid. Võib-olla aastaid.',
  text_tr = 'Üç gün sonra dişlerini fırçalarken aynada görüyorsun: bir beyaz saç teli. Yirmi yaşındasın — beyaz saç olmamalı. Kitaba bakıyorsun. Bir sayfa kaybolmuş — yazdığın sayfa. Ve aniden anlıyorsun: kitap hiçbir şeyi bedava vermiyor. Her dilek senden zaman ALIYOR. Günler. Aylar. Belki yıllar.'
WHERE story_id = 'fate_book' AND id = 'd1_price';

-- The stranger — chilling reveal
UPDATE story_nodes SET
  text_ee = 'Tuba muutub külmaks. Küünal kustub. Ja pimedusest astub välja kuju — pikk, kõhn, musta mantliga. Tema nägu on kummaliselt tuttav. "Huvitav valik," ütleb ta häälega, mis kõlab nagu kaks häält korraga. "Sa ohverdasid enda aega TEISE inimese eest. Seda juhtub harva. Enamasti kirjutavad inimesed enda soove. Raha. Võimu. Ilu." Ta peatub. "Ma olen raamatu looja. Ja nüüd ma tahan seda tagasi."',
  text_tr = 'Oda soğuyor. Mum sönüyor. Ve karanlıktan bir figür çıkıyor — uzun, zayıf, siyah pelerinli. Yüzü tuhaf şekilde tanıdık. "İlginç bir seçim," diyor iki ses aynı anda konuşurmuş gibi. "Başka biri için kendi zamanını feda ettin. Bu nadiren olur. Çoğu insan kendi dileklerini yazar. Para. Güç. Güzellik." Duruyor. "Ben kitabın yaratıcısıyım. Ve şimdi onu geri istiyorum."'
WHERE story_id = 'fate_book' AND id = 'd1_stranger';

-- Ending — haunting question
UPDATE story_nodes SET
  text_ee = 'Sa ulatad raamatu. Võõras võtab selle, noogutab ja astub tagasi pimedasse. Küünal süttib uuesti. Tuba on soe. Sa vaatad peeglisse — su nägu on jälle noor. Nagu midagi ei oleks juhtunud. Aga su käed värisevad. Ja üks küsimus ei lase sul magada: "Kui raamat tuleks tagasi... kas ma kasutaksin seda uuesti?"',
  text_tr = 'Kitabı uzatıyorsun. Yabancı alıyor, başını sallayıp karanlığa geri adımlıyor. Mum tekrar yanıyor. Oda sıcak. Aynaya bakıyorsun — yüzün yine genç. Sanki hiçbir şey olmamış. Ama ellerin titriyor. Ve bir soru uyumana engel oluyor: "Kitap geri gelse... tekrar kullanır mıydım?"'
WHERE story_id = 'fate_book' AND id = 'd1_end';
