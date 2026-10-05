--liquibase formatted sql

--changeset flashcard:070 splitStatements:true
ALTER TABLE grammar_lessons ADD COLUMN IF NOT EXISTS podcast_script JSONB;

--changeset flashcard:071 splitStatements:false
-- Pre-generated podcast scripts so the scheduler only needs TTS, not Gemini

UPDATE grammar_lessons SET podcast_script = '[
  {"text": "Merhaba! Bugün Estoncada saat söylemeyi öğreneceğiz.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Estoncada saatler biraz farklıdır. Tam saatler için <<kell on>> ve ardından sayı söylenir. Yarım ve çeyrek saatler ise bir sonraki saate göre sayılır.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Kell on üks.", "language": "et", "pause_after_ms": 2500},
  {"text": "Saat bir.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Kell on pool seitse.", "language": "et", "pause_after_ms": 2500},
  {"text": "Saat altı buçuk. <<Pool>> yarım demek ama bir sonraki saate göre sayılır.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Kell on veerand kaks.", "language": "et", "pause_after_ms": 2500},
  {"text": "Saat bir çeyrek geçiyor. <<Veerand>> çeyrek demek.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Kell on kolmveerand kaks.", "language": "et", "pause_after_ms": 2500},
  {"text": "Saat ikiye çeyrek var. <<Kolmveerand>> üç çeyrek demek.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Şimdi siz deneyin! Saat beş buçuk Estoncada nasıl söylenir?", "language": "tr", "pause_after_ms": 3000},
  {"text": "Cevap: Kell on pool kuus.", "language": "et", "pause_after_ms": 2000},
  {"text": "Harika! Estoncada saat söylemeyi öğrendiniz. Sonraki derste görüşürüz!", "language": "tr", "pause_after_ms": 1000}
]'::jsonb WHERE id = 'a1-telling-time';

UPDATE grammar_lessons SET podcast_script = '[
  {"text": "Merhaba! Bugün kıyafetlerin vücutta nereye giyildiğini Estoncada öğreneceğiz.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Estoncada her kıyafet için vücuttaki yeri belirten özel kelimeler vardır. <<Peas>> başta, <<seljas>> sırtta, <<jalas>> ayakta demek.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Tal on müts peas.", "language": "et", "pause_after_ms": 2500},
  {"text": "Onun başında şapka var. <<Peas>> başta demek.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Tal on jope seljas.", "language": "et", "pause_after_ms": 2500},
  {"text": "Onun sırtında mont var. <<Seljas>> sırtta demek.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Tal on saapad jalas.", "language": "et", "pause_after_ms": 2500},
  {"text": "Onun ayağında çizmeler var. <<Jalas>> ayakta demek.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Tal on kindad käes.", "language": "et", "pause_after_ms": 2500},
  {"text": "Onun elinde eldivenler var. <<Käes>> elde demek.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Şimdi siz deneyin! Boyundaki atkı için ne dersiniz?", "language": "tr", "pause_after_ms": 3000},
  {"text": "Cevap: Tal on sall kaelas.", "language": "et", "pause_after_ms": 2000},
  {"text": "Çok güzel! Kıyafet gramerini öğrendiniz. Sonraki derste görüşürüz!", "language": "tr", "pause_after_ms": 1000}
]'::jsonb WHERE id = 'a1-clothing-locations';

UPDATE grammar_lessons SET podcast_script = '[
  {"text": "Merhaba! Bugün Estoncada ile ve sız eklerini öğreneceğiz.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Estoncada ile anlamı için kelimeye <<ga>> eki, sız anlamı için <<ta>> eki eklenir. Her ikisi de tamlayan haline eklenir.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Ma joon kohvi piimaga.", "language": "et", "pause_after_ms": 2500},
  {"text": "Kahvemi sütle içerim. <<Piimaga>> sütle demek.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Ma sõidan autoga.", "language": "et", "pause_after_ms": 2500},
  {"text": "Arabayla gidiyorum. <<Autoga>> arabayla demek.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Kohv suhkruta, palun.", "language": "et", "pause_after_ms": 2500},
  {"text": "Şekersiz kahve lütfen. <<Suhkruta>> şekersiz demek.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Ma olen ilma autota.", "language": "et", "pause_after_ms": 2500},
  {"text": "Arabasızım. <<Autota>> arabasız demek.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Şimdi siz deneyin! Sinuga ne demek?", "language": "tr", "pause_after_ms": 3000},
  {"text": "Cevap: seninle demek. <<Sina>> sen, <<sinuga>> seninle.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Harika! Bugün ile ve sız eklerini öğrendiniz. Sonraki derste görüşürüz!", "language": "tr", "pause_after_ms": 1000}
]'::jsonb WHERE id = 'a1-comitative-abessive';

UPDATE grammar_lessons SET podcast_script = '[
  {"text": "Merhaba! Bugün Estoncada mastar sistemini öğreneceğiz. İki tür mastar var: <<ma>> mastarı ve <<da>> mastarı.", "language": "tr", "pause_after_ms": 2000},
  {"text": "<<Ma>> mastarı hareket ve zorunluluk fiillerinden sonra kullanılır. <<Da>> mastarı ise istek, duygu ve yetenek fiillerinden sonra gelir.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Ma lähen õppima.", "language": "et", "pause_after_ms": 2500},
  {"text": "Öğrenmeye gidiyorum. Gitmek bir hareket fiili, bu yüzden <<õppima>> kullanılır.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Ma hakkan töötama.", "language": "et", "pause_after_ms": 2500},
  {"text": "Çalışmaya başlıyorum. Başlamak bir zorunluluk fiili.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Ma tahan õppida.", "language": "et", "pause_after_ms": 2500},
  {"text": "Öğrenmek istiyorum. İstemek bir istek fiili, bu yüzden <<õppida>> kullanılır.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Mulle meeldib tantsida.", "language": "et", "pause_after_ms": 2500},
  {"text": "Dans etmekten hoşlanırım. Hoşlanmak bir duygu fiili.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Şimdi siz deneyin! Ma oskan... ujuda mı, ujuma mı?", "language": "tr", "pause_after_ms": 3000},
  {"text": "Cevap: Ma oskan ujuda. Yapabilmek bir yetenek fiili, <<da>> mastarı kullanılır.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Tebrikler! Mastar sistemini öğrendiniz. Sonraki derste görüşürüz!", "language": "tr", "pause_after_ms": 1000}
]'::jsonb WHERE id = 'a1-ma-da-infinitive';

-- B1 Passive Voice (if exists in DB)
UPDATE grammar_lessons SET podcast_script = '[
  {"text": "Merhaba! Bugün Estoncada edilgen çatıyı, yani <<umbisikuline tegumood>> konusunu öğreneceğiz.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Estoncada edilgen çatı, eylemi kimin yaptığı belli olmadığında kullanılır. Fiil köküne <<takse>> veya <<dakse>> eki gelir.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Siin räägitakse eesti keelt.", "language": "et", "pause_after_ms": 2500},
  {"text": "Burada Estonca konuşulur. <<Räägitakse>> konuşulmak demek.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Uks avatakse kell kaheksa.", "language": "et", "pause_after_ms": 2500},
  {"text": "Kapı saat sekizde açılır. <<Avatakse>> açılmak demek.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Seda raamatut loetakse palju.", "language": "et", "pause_after_ms": 2500},
  {"text": "Bu kitap çok okunur. <<Loetakse>> okunmak demek.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Geçmiş zamanda ise <<ti>> eki kullanılır.", "language": "tr", "pause_after_ms": 1500},
  {"text": "Maja ehitati eelmisel aastal.", "language": "et", "pause_after_ms": 2500},
  {"text": "Ev geçen yıl inşa edildi. <<Ehitati>> inşa edildi demek.", "language": "tr", "pause_after_ms": 2000},
  {"text": "Şimdi siz deneyin! Burada süt içilir nasıl dersiniz?", "language": "tr", "pause_after_ms": 3000},
  {"text": "Cevap: Siin juuakse piima.", "language": "et", "pause_after_ms": 2000},
  {"text": "Harika! Edilgen çatıyı öğrendiniz. Sonraki derste görüşürüz!", "language": "tr", "pause_after_ms": 1000}
]'::jsonb WHERE id = 'b1-passive';
