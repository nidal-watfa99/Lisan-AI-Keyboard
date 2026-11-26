# سياسة خصوصية لسان | Lisan Privacy Policy

## العربية

لا يطلب لسان حساباً ولا يسجل ضغطات المفاتيح أو كلمات المرور. عند استخدام الاقتراحات أو التحرير، يعالج النص المطلوب مؤقتاً في الذاكرة. يحجب لسان أدوات الترجمة والإملاء والحافظة والاقتراحات في حقول كلمات المرور والحقول التي تطلب عدم التعلم الشخصي. تعتمد معرفة نوع الحقل على الوصف الذي يقدمه تطبيق التحرير إلى أندرويد؛ لا يوجد ضمان لاكتشاف جميع المعلومات الحساسة التي تُكتب في حقل نص عادي.

تعمل الترجمة الافتراضية وكشف لغة النص عبر Google ML Kit على الجهاز. يحتاج تنزيل النماذج إلى اتصال بخدمات Google، وقد تخضع مكتبات Google لمعالجة بيانات تشغيلية وفق وثائقها. ترجمة الكيبورد محلية دائماً. إذا ضبطت خادم ترجمة HTTPS من الشاشة المستقلة، يستقبل الخادم النص المعروض واختيارات اللغات عند تأكيد كل طلب فقط؛ يحدد مشغّل الخادم سياسة الاحتفاظ. لا يُستعاد عنوان الخادم عند استيراد الإعدادات. لا تُرسل نصوص لمجرد حفظ العنوان أو لصق نص أو فتح الشاشة.

يستخدم Whisper الذاكرة أثناء الإملاء ولا يحفظ التسجيلات ولا يرسلها. عند تنزيل النموذج يتصل لسان بـ Hugging Face للحصول على الملف فقط. إذا اخترت محرك أندرويد الاختياري الذي قد يستخدم الشبكة، فقد يعالج مزوّده الصوت عند بدء الإملاء وفق شروطه.

لا يراقب لسان الحافظة في الخلفية. تُقرأ فقط بإجراء واضح، وتُستبعد العناصر التي يعرّفها النظام أو تطبيق النسخ بأنها حساسة عند قراءة الحافظة مباشرة. تُحفَظ العبارات والكلمات والمفضلة صراحة، وسجل الترجمات والإيموجي الأخيرة اختياري. تستخدم هذه المكتبات تخزيناً محلياً مشفراً باستخدام Android Keystore؛ لا تُضمّن محتوياتها في نسخة JSON الاحتياطية. يمكنك مسحها من «الخصوصية والأمان»، وحذف نماذج الترجمة بشكل منفصل. لا يؤدي إيقاف السجل إلى حذف العناصر القديمة؛ استخدم زر المسح لذلك.

يُحضَّر تقرير المشكلة للمعاينة أولاً، ويضم فقط الوصف الذي تكتبه وبيانات الجهاز التي تختار تضمينها، ولا يُرسل تلقائياً. مشاركة النص تفتح أدوات المشاركة النظامية. للاستفسار: `nidalwatfa99@gmail.com`.

## English

Lisan does not require an account or keep a keystroke or password log. Editing and suggestions can process relevant text transiently in memory. Password fields and editors opting out of personalized learning do not expose translation, dictation, clipboard or prediction tools. This relies on the editor declaring the input type accurately; ordinary text boxes might contain private content that cannot be identified automatically.

Google ML Kit performs default translation and language identification on-device. Model downloads contact Google; its libraries may handle operational data according to their documentation. Keyboard translation always uses ML Kit locally. If you configure an HTTPS endpoint in the standalone screen, each confirmed request delivers the reviewed text and selected languages to that server; its operator determines retention. Saving an address, pasting text, or opening the screen does not submit text. The address is excluded from settings exports and cleared on settings import.

Whisper uses temporary memory and neither saves nor uploads audio. Its model download contacts Hugging Face to obtain the model file only. If you opt in to the network-capable Android system recognizer and start dictation, the chosen provider may use its servers subject to its own terms.

The app never monitors the clipboard in the background. Direct clipboard reads are explicit and exclude clips marked sensitive when that metadata is provided. Saved phrases, words, favorites and optional histories use local Android Keystore-backed encryption and are excluded from JSON exports. Clear them under Privacy, manage models separately, or clear all app storage in Android. Disabling a history does not itself remove saved entries.

Problem reports require review; there is no automatic submission or raw log attachment. Sharing uses Android’s Sharesheet. Contact: `nidalwatfa99@gmail.com`.

See [Google ML Kit translation for Android](https://developers.google.com/ml-kit/language/translation/android) and [Google ML Kit language identification](https://developers.google.com/ml-kit/language/identification/android) for implementation details.
