package com.thechameleons.chameleonnav.engine

import com.thechameleons.chameleonnav.model.Destination
import com.thechameleons.chameleonnav.model.GeoPoint

/**
 * Comprehensive Pan-India Offline Locations Database.
 * Contains 90+ curated cities, district headquarters, local transit hubs,
 * educational centers, and pilgrimage destinations across all states of India.
 */
object IndiaLocationsDatabase {

    val allDestinations: List<Destination> = listOf(
        Destination(
            id = "loc_shaheed_sthal__new_bus_adda",
            name = "Shaheed Sthal (New Bus Adda)",
            location = GeoPoint(28.672, 77.429),
            category = "Local & NCR",
            description = "Ghaziabad Red Line Metro Terminal, Meerut Road",
            keywords = listOf("saheed", "shaheed", "sthal", "new bus adda", "ghaziabad metro", "red line", "up")
        ),
        Destination(
            id = "loc_duhai_rrts_station",
            name = "Duhai RRTS Station",
            location = GeoPoint(28.742, 77.489),
            category = "Local & NCR",
            description = "Namo Bharat (RAPIDX) Station, Meerut Road",
            keywords = listOf("duhai", "rrts", "namo bharat", "rapidx", "meerut road", "up")
        ),
        Destination(
            id = "loc_duhai_depot",
            name = "Duhai Depot",
            location = GeoPoint(28.751, 77.498),
            category = "Local & NCR",
            description = "RRTS Train Maintenance Depot, Basantpur",
            keywords = listOf("duhai depot", "depot", "rrts depot", "basantpur")
        ),
        Destination(
            id = "loc_kiet_group_of_institutions",
            name = "KIET Group of Institutions",
            location = GeoPoint(28.7532, 77.4975),
            category = "Local & NCR",
            description = "Engineering College, NH-58 Meerut Road, Muradnagar",
            keywords = listOf("kiet", "college", "engineering", "muradnagar", "meerut road")
        ),
        Destination(
            id = "loc_morta__hrit_crossing",
            name = "Morta (HRIT Crossing)",
            location = GeoPoint(28.718, 77.464),
            category = "Local & NCR",
            description = "Morta Village & HRIT College, Meerut Road",
            keywords = listOf("morta", "hrit", "meerut road", "crossing")
        ),
        Destination(
            id = "loc_raj_nagar_extension",
            name = "Raj Nagar Extension",
            location = GeoPoint(28.705, 77.432),
            category = "Local & NCR",
            description = "River Heights, Elevated Road Junction, Ghaziabad",
            keywords = listOf("raj nagar", "raj nagar extension", "rne", "river heights")
        ),
        Destination(
            id = "loc_hindon_river_metro_station",
            name = "Hindon River Metro Station",
            location = GeoPoint(28.6785, 77.4172),
            category = "Local & NCR",
            description = "Red Line Metro Station, City Forest / GDA",
            keywords = listOf("hindon", "hindon river", "city forest", "metro")
        ),
        Destination(
            id = "loc_mohan_nagar_temple___metro",
            name = "Mohan Nagar Temple & Metro",
            location = GeoPoint(28.6805, 77.3885),
            category = "Local & NCR",
            description = "Mohan Nagar Junction, Red Line Metro & Temple",
            keywords = listOf("mohan nagar", "mandir", "temple", "ghaziabad")
        ),
        Destination(
            id = "loc_ghaziabad_old_bus_stand",
            name = "Ghaziabad Old Bus Stand",
            location = GeoPoint(28.665, 77.438),
            category = "Local & NCR",
            description = "Ghaziabad Central Bus Station & Clock Tower",
            keywords = listOf("ghaziabad bus stand", "purana bus adda", "ghantaghar")
        ),
        Destination(
            id = "loc_patel_nagar___rdc_ghaziabad",
            name = "Patel Nagar / RDC Ghaziabad",
            location = GeoPoint(28.673, 77.445),
            category = "Local & NCR",
            description = "Raj Nagar District Center (RDC), Commercial Hub",
            keywords = listOf("rdc", "patel nagar", "raj nagar", "commercial")
        ),
        Destination(
            id = "loc_anand_vihar_isbt___railway",
            name = "Anand Vihar ISBT & Railway",
            location = GeoPoint(28.6469, 77.316),
            category = "Local & NCR",
            description = "Major Transit Hub, Delhi-UP Border",
            keywords = listOf("anand vihar", "isbt", "railway station", "metro")
        ),
        Destination(
            id = "loc_vaishali_metro_station",
            name = "Vaishali Metro Station",
            location = GeoPoint(28.6499, 77.3396),
            category = "Local & NCR",
            description = "Blue Line Metro Terminal, Ghaziabad",
            keywords = listOf("vaishali", "blue line", "metro")
        ),
        Destination(
            id = "loc_noida_sector_62",
            name = "Noida Sector 62",
            location = GeoPoint(28.6276, 77.3725),
            category = "Local & NCR",
            description = "Electronic City Metro, IT & Institutional Hub",
            keywords = listOf("noida 62", "sector 62", "electronic city")
        ),
        Destination(
            id = "loc_ghaziabad_city",
            name = "Ghaziabad City",
            location = GeoPoint(28.6692, 77.4538),
            category = "Uttar Pradesh",
            description = "Industrial & Transit Hub, NCR",
            keywords = listOf("ghaziabad", "gzb", "up")
        ),
        Destination(
            id = "loc_noida_city",
            name = "Noida City",
            location = GeoPoint(28.5355, 77.391),
            category = "Uttar Pradesh",
            description = "IT & Commercial Hub, NCR",
            keywords = listOf("noida", "gautam buddha nagar", "up")
        ),
        Destination(
            id = "loc_greater_noida",
            name = "Greater Noida",
            location = GeoPoint(28.4744, 77.504),
            category = "Uttar Pradesh",
            description = "Pari Chowk & Expressway Hub",
            keywords = listOf("greater noida", "gr noida", "pari chowk", "up")
        ),
        Destination(
            id = "loc_meerut",
            name = "Meerut",
            location = GeoPoint(28.9845, 77.7064),
            category = "Uttar Pradesh",
            description = "Historical & Sports Goods City",
            keywords = listOf("meerut", "cantt", "up")
        ),
        Destination(
            id = "loc_hapur",
            name = "Hapur",
            location = GeoPoint(28.7306, 77.7759),
            category = "Uttar Pradesh",
            description = "Grain Market & Highway Junction",
            keywords = listOf("hapur", "up")
        ),
        Destination(
            id = "loc_bulandshahr",
            name = "Bulandshahr",
            location = GeoPoint(28.4069, 77.8498),
            category = "Uttar Pradesh",
            description = "Western UP District",
            keywords = listOf("bulandshahr", "up")
        ),
        Destination(
            id = "loc_modinagar",
            name = "Modinagar",
            location = GeoPoint(28.8317, 77.5804),
            category = "Uttar Pradesh",
            description = "Industrial & Education Town, NH-58",
            keywords = listOf("modinagar", "meerut road", "up")
        ),
        Destination(
            id = "loc_muradnagar",
            name = "Muradnagar",
            location = GeoPoint(28.7758, 77.5028),
            category = "Uttar Pradesh",
            description = "Ordnance Factory & Rapid Rail Town",
            keywords = listOf("muradnagar", "kiet", "up")
        ),
        Destination(
            id = "loc_aligarh",
            name = "Aligarh",
            location = GeoPoint(27.8974, 78.088),
            category = "Uttar Pradesh",
            description = "AMU & Lock City",
            keywords = listOf("aligarh", "amu", "up")
        ),
        Destination(
            id = "loc_agra",
            name = "Agra",
            location = GeoPoint(27.1767, 78.0081),
            category = "Uttar Pradesh",
            description = "Taj Mahal & Yamuna Expressway",
            keywords = listOf("agra", "taj mahal", "up")
        ),
        Destination(
            id = "loc_mathura",
            name = "Mathura",
            location = GeoPoint(27.4924, 77.6737),
            category = "Uttar Pradesh",
            description = "Shri Krishna Janmabhoomi",
            keywords = listOf("mathura", "up")
        ),
        Destination(
            id = "loc_vrindavan",
            name = "Vrindavan",
            location = GeoPoint(27.5806, 77.7006),
            category = "Uttar Pradesh",
            description = "Banke Bihari Mandir & Prem Mandir",
            keywords = listOf("vrindavan", "banke bihari", "up")
        ),
        Destination(
            id = "loc_bareilly",
            name = "Bareilly",
            location = GeoPoint(28.367, 79.4304),
            category = "Uttar Pradesh",
            description = "Jhumka City, Rohilkhand",
            keywords = listOf("bareilly", "up")
        ),
        Destination(
            id = "loc_moradabad",
            name = "Moradabad",
            location = GeoPoint(28.8386, 78.7733),
            category = "Uttar Pradesh",
            description = "Brass City of India",
            keywords = listOf("moradabad", "up")
        ),
        Destination(
            id = "loc_saharanpur",
            name = "Saharanpur",
            location = GeoPoint(29.9671, 77.551),
            category = "Uttar Pradesh",
            description = "Wood Carving & Agriculture Hub",
            keywords = listOf("saharanpur", "up")
        ),
        Destination(
            id = "loc_muzaffarnagar",
            name = "Muzaffarnagar",
            location = GeoPoint(29.4727, 77.7085),
            category = "Uttar Pradesh",
            description = "Sugar Capital of UP, NH-58",
            keywords = listOf("muzaffarnagar", "up")
        ),
        Destination(
            id = "loc_lucknow",
            name = "Lucknow",
            location = GeoPoint(26.8467, 80.9462),
            category = "Uttar Pradesh",
            description = "Capital of Uttar Pradesh",
            keywords = listOf("lucknow", "lko", "up")
        ),
        Destination(
            id = "loc_alambagh__lucknow",
            name = "Alambagh (Lucknow)",
            location = GeoPoint(26.815, 80.898),
            category = "Uttar Pradesh",
            description = "Alambagh Bus Terminal & Metro, Lucknow",
            keywords = listOf("alambagh", "lucknow", "up")
        ),
        Destination(
            id = "loc_kanpur",
            name = "Kanpur",
            location = GeoPoint(26.4499, 80.3319),
            category = "Uttar Pradesh",
            description = "Major Industrial & Educational Metropolis",
            keywords = listOf("kanpur", "up")
        ),
        Destination(
            id = "loc_ayodhya",
            name = "Ayodhya",
            location = GeoPoint(26.7922, 82.1998),
            category = "Uttar Pradesh",
            description = "Shri Ram Janmabhoomi Mandir",
            keywords = listOf("ayodhya", "ram mandir", "up")
        ),
        Destination(
            id = "loc_varanasi",
            name = "Varanasi",
            location = GeoPoint(25.3176, 82.9739),
            category = "Uttar Pradesh",
            description = "Kashi Vishwanath & Ganga Ghats",
            keywords = listOf("varanasi", "kashi", "banaras", "up")
        ),
        Destination(
            id = "loc_prayagraj",
            name = "Prayagraj",
            location = GeoPoint(25.4358, 81.8463),
            category = "Uttar Pradesh",
            description = "Triveni Sangam & High Court",
            keywords = listOf("prayagraj", "allahabad", "sangam", "up")
        ),
        Destination(
            id = "loc_gorakhpur",
            name = "Gorakhpur",
            location = GeoPoint(26.7606, 83.3732),
            category = "Uttar Pradesh",
            description = "Gorakhnath Temple & NE Railway HQ",
            keywords = listOf("gorakhpur", "up")
        ),
        Destination(
            id = "loc_jhansi",
            name = "Jhansi",
            location = GeoPoint(25.4484, 78.5685),
            category = "Uttar Pradesh",
            description = "Bundelkhand Gateway & Rani Fort",
            keywords = listOf("jhansi", "up")
        ),
        Destination(
            id = "loc_new_delhi__india_gate",
            name = "New Delhi (India Gate)",
            location = GeoPoint(28.6129, 77.2295),
            category = "Delhi NCR",
            description = "National Monument & Central Vista",
            keywords = listOf("delhi", "new delhi", "india gate")
        ),
        Destination(
            id = "loc_connaught_place",
            name = "Connaught Place",
            location = GeoPoint(28.6315, 77.2167),
            category = "Delhi NCR",
            description = "Central Business District, Rajiv Chowk",
            keywords = listOf("cp", "connaught place", "rajiv chowk")
        ),
        Destination(
            id = "loc_indira_gandhi_airport__del",
            name = "Indira Gandhi Airport (DEL)",
            location = GeoPoint(28.5562, 77.1),
            category = "Delhi NCR",
            description = "International Airport Terminal 3",
            keywords = listOf("igi", "airport", "delhi airport")
        ),
        Destination(
            id = "loc_gurugram",
            name = "Gurugram",
            location = GeoPoint(28.4595, 77.0266),
            category = "Haryana",
            description = "Cyber City & Financial Hub, Millennium City",
            keywords = listOf("gurugram", "gurgaon", "cyber city", "haryana")
        ),
        Destination(
            id = "loc_faridabad",
            name = "Faridabad",
            location = GeoPoint(28.4089, 77.3178),
            category = "Haryana",
            description = "Major Industrial City, NCR",
            keywords = listOf("faridabad", "haryana")
        ),
        Destination(
            id = "loc_panipat",
            name = "Panipat",
            location = GeoPoint(29.3909, 76.9635),
            category = "Haryana",
            description = "Textile City & Historical Battlefield, GT Road",
            keywords = listOf("panipat", "haryana")
        ),
        Destination(
            id = "loc_sonipat",
            name = "Sonipat",
            location = GeoPoint(28.9931, 77.0151),
            category = "Haryana",
            description = "Education Hub & Industrial Town, NH-44",
            keywords = listOf("sonipat", "haryana")
        ),
        Destination(
            id = "loc_karnal",
            name = "Karnal",
            location = GeoPoint(29.6857, 76.9905),
            category = "Haryana",
            description = "Rice Bowl of India & Dairy Institute, GT Road",
            keywords = listOf("karnal", "haryana")
        ),
        Destination(
            id = "loc_ambala",
            name = "Ambala",
            location = GeoPoint(30.3782, 76.7767),
            category = "Haryana",
            description = "Twin City & Major Railway Junction",
            keywords = listOf("ambala", "haryana")
        ),
        Destination(
            id = "loc_rohtak",
            name = "Rohtak",
            location = GeoPoint(28.8955, 76.6066),
            category = "Haryana",
            description = "Education Hub & Cloth Market",
            keywords = listOf("rohtak", "haryana")
        ),
        Destination(
            id = "loc_hisar",
            name = "Hisar",
            location = GeoPoint(29.1492, 75.7217),
            category = "Haryana",
            description = "Steel City & Agricultural University",
            keywords = listOf("hisar", "haryana")
        ),
        Destination(
            id = "loc_chandigarh",
            name = "Chandigarh",
            location = GeoPoint(30.7333, 76.7794),
            category = "Chandigarh",
            description = "Joint Capital of Punjab & Haryana",
            keywords = listOf("chandigarh", "chd")
        ),
        Destination(
            id = "loc_amritsar",
            name = "Amritsar",
            location = GeoPoint(31.634, 74.8723),
            category = "Punjab",
            description = "Golden Temple (Harmandir Sahib)",
            keywords = listOf("amritsar", "golden temple", "punjab")
        ),
        Destination(
            id = "loc_ludhiana",
            name = "Ludhiana",
            location = GeoPoint(30.901, 75.8573),
            category = "Punjab",
            description = "Industrial Capital & Hosiery Hub",
            keywords = listOf("ludhiana", "punjab")
        ),
        Destination(
            id = "loc_jalandhar",
            name = "Jalandhar",
            location = GeoPoint(31.326, 75.5762),
            category = "Punjab",
            description = "Sports Goods Manufacturing Hub",
            keywords = listOf("jalandhar", "punjab")
        ),
        Destination(
            id = "loc_patiala",
            name = "Patiala",
            location = GeoPoint(30.3398, 76.3869),
            category = "Punjab",
            description = "Royal City of Punjab",
            keywords = listOf("patiala", "punjab")
        ),
        Destination(
            id = "loc_jaipur",
            name = "Jaipur",
            location = GeoPoint(26.9124, 75.7873),
            category = "Rajasthan",
            description = "Pink City & Capital of Rajasthan",
            keywords = listOf("jaipur", "pink city", "rajasthan")
        ),
        Destination(
            id = "loc_udaipur",
            name = "Udaipur",
            location = GeoPoint(24.5854, 73.7125),
            category = "Rajasthan",
            description = "City of Lakes & Palaces",
            keywords = listOf("udaipur", "rajasthan")
        ),
        Destination(
            id = "loc_jodhpur",
            name = "Jodhpur",
            location = GeoPoint(26.2389, 73.0243),
            category = "Rajasthan",
            description = "Blue City & Mehrangarh Fort",
            keywords = listOf("jodhpur", "rajasthan")
        ),
        Destination(
            id = "loc_kota",
            name = "Kota",
            location = GeoPoint(25.2138, 75.8648),
            category = "Rajasthan",
            description = "Coaching Hub of India & Chambal River",
            keywords = listOf("kota", "rajasthan")
        ),
        Destination(
            id = "loc_ajmer",
            name = "Ajmer",
            location = GeoPoint(26.4499, 74.6399),
            category = "Rajasthan",
            description = "Dargah Sharif & Pushkar Lake",
            keywords = listOf("ajmer", "pushkar", "rajasthan")
        ),
        Destination(
            id = "loc_bikaner",
            name = "Bikaner",
            location = GeoPoint(28.0229, 73.3119),
            category = "Rajasthan",
            description = "Junagarh Fort & Camel Country",
            keywords = listOf("bikaner", "rajasthan")
        ),
        Destination(
            id = "loc_alwar",
            name = "Alwar",
            location = GeoPoint(27.553, 76.6346),
            category = "Rajasthan",
            description = "Sariska Tiger Reserve Gateway, NCR",
            keywords = listOf("alwar", "rajasthan")
        ),
        Destination(
            id = "loc_dehradun",
            name = "Dehradun",
            location = GeoPoint(30.3165, 78.0322),
            category = "Uttarakhand",
            description = "Capital of Uttarakhand, Doon Valley",
            keywords = listOf("dehradun", "uttarakhand")
        ),
        Destination(
            id = "loc_haridwar",
            name = "Haridwar",
            location = GeoPoint(29.9457, 78.1642),
            category = "Uttarakhand",
            description = "Har Ki Pauri & Holy Ganga Gateway",
            keywords = listOf("haridwar", "uttarakhand")
        ),
        Destination(
            id = "loc_rishikesh",
            name = "Rishikesh",
            location = GeoPoint(30.0869, 78.2676),
            category = "Uttarakhand",
            description = "Yoga Capital of the World",
            keywords = listOf("rishikesh", "uttarakhand")
        ),
        Destination(
            id = "loc_nainital",
            name = "Nainital",
            location = GeoPoint(29.3919, 79.4542),
            category = "Uttarakhand",
            description = "Lake City & Kumaon Hills",
            keywords = listOf("nainital", "uttarakhand")
        ),
        Destination(
            id = "loc_shimla",
            name = "Shimla",
            location = GeoPoint(31.1048, 77.1734),
            category = "Himachal Pradesh",
            description = "Queen of Hills & Capital of HP",
            keywords = listOf("shimla", "himachal")
        ),
        Destination(
            id = "loc_manali",
            name = "Manali",
            location = GeoPoint(32.2432, 77.1892),
            category = "Himachal Pradesh",
            description = "Kullu Valley & Solang Valley",
            keywords = listOf("manali", "himachal")
        ),
        Destination(
            id = "loc_dharamshala",
            name = "Dharamshala",
            location = GeoPoint(32.219, 76.3234),
            category = "Himachal Pradesh",
            description = "Dalai Lama Residence & Cricket Stadium",
            keywords = listOf("dharamshala", "mcleodganj", "himachal")
        ),
        Destination(
            id = "loc_mumbai",
            name = "Mumbai",
            location = GeoPoint(19.076, 72.8777),
            category = "Maharashtra",
            description = "Financial Capital of India, Gateway of India",
            keywords = listOf("mumbai", "bombay", "maharashtra")
        ),
        Destination(
            id = "loc_pune",
            name = "Pune",
            location = GeoPoint(18.5204, 73.8567),
            category = "Maharashtra",
            description = "Oxford of the East & IT Metropolis",
            keywords = listOf("pune", "maharashtra")
        ),
        Destination(
            id = "loc_nagpur",
            name = "Nagpur",
            location = GeoPoint(21.1458, 79.0882),
            category = "Maharashtra",
            description = "Orange City & Zero Mile Center of India",
            keywords = listOf("nagpur", "maharashtra")
        ),
        Destination(
            id = "loc_nashik",
            name = "Nashik",
            location = GeoPoint(19.9975, 73.7898),
            category = "Maharashtra",
            description = "Wine Capital & Kumbh Mela City",
            keywords = listOf("nashik", "maharashtra")
        ),
        Destination(
            id = "loc_chhatrapati_sambhajinagar",
            name = "Chhatrapati Sambhajinagar",
            location = GeoPoint(19.8762, 75.3433),
            category = "Maharashtra",
            description = "Ajanta & Ellora Caves Gateway",
            keywords = listOf("aurangabad", "sambhajinagar", "maharashtra")
        ),
        Destination(
            id = "loc_ahmedabad",
            name = "Ahmedabad",
            location = GeoPoint(23.0225, 72.5714),
            category = "Gujarat",
            description = "Sabarmati Ashram & Business Capital",
            keywords = listOf("ahmedabad", "gujarat")
        ),
        Destination(
            id = "loc_surat",
            name = "Surat",
            location = GeoPoint(21.1702, 72.8311),
            category = "Gujarat",
            description = "Diamond & Textile Metropolis",
            keywords = listOf("surat", "gujarat")
        ),
        Destination(
            id = "loc_vadodara",
            name = "Vadodara",
            location = GeoPoint(22.3072, 73.1812),
            category = "Gujarat",
            description = "Cultural Capital & Laxmi Vilas Palace",
            keywords = listOf("vadodara", "baroda", "gujarat")
        ),
        Destination(
            id = "loc_rajkot",
            name = "Rajkot",
            location = GeoPoint(22.3039, 70.8022),
            category = "Gujarat",
            description = "Saurashtra Commercial Hub",
            keywords = listOf("rajkot", "gujarat")
        ),
        Destination(
            id = "loc_bhopal",
            name = "Bhopal",
            location = GeoPoint(23.2599, 77.4126),
            category = "Madhya Pradesh",
            description = "City of Lakes & MP Capital",
            keywords = listOf("bhopal", "mp")
        ),
        Destination(
            id = "loc_indore",
            name = "Indore",
            location = GeoPoint(22.7196, 75.8577),
            category = "Madhya Pradesh",
            description = "Cleanest City of India & Food Capital",
            keywords = listOf("indore", "mp")
        ),
        Destination(
            id = "loc_gwalior",
            name = "Gwalior",
            location = GeoPoint(26.2183, 78.1828),
            category = "Madhya Pradesh",
            description = "Gwalior Fort & Tansen City",
            keywords = listOf("gwalior", "mp")
        ),
        Destination(
            id = "loc_jabalpur",
            name = "Jabalpur",
            location = GeoPoint(23.1815, 79.9864),
            category = "Madhya Pradesh",
            description = "Bhedaghat Marble Rocks & High Court",
            keywords = listOf("jabalpur", "mp")
        ),
        Destination(
            id = "loc_ujjain",
            name = "Ujjain",
            location = GeoPoint(23.1765, 75.7885),
            category = "Madhya Pradesh",
            description = "Mahakaleshwar Jyotirlinga",
            keywords = listOf("ujjain", "mp")
        ),
        Destination(
            id = "loc_raipur",
            name = "Raipur",
            location = GeoPoint(21.2514, 81.6296),
            category = "Chhattisgarh",
            description = "Capital of Chhattisgarh",
            keywords = listOf("raipur", "chhattisgarh")
        ),
        Destination(
            id = "loc_patna",
            name = "Patna",
            location = GeoPoint(25.5941, 85.1376),
            category = "Bihar",
            description = "Historical Pataliputra & Capital of Bihar",
            keywords = listOf("patna", "bihar")
        ),
        Destination(
            id = "loc_gaya",
            name = "Gaya",
            location = GeoPoint(24.7914, 85.0002),
            category = "Bihar",
            description = "Bodh Gaya & Mahabodhi Temple",
            keywords = listOf("gaya", "bodhgaya", "bihar")
        ),
        Destination(
            id = "loc_muzaffarpur",
            name = "Muzaffarpur",
            location = GeoPoint(26.1209, 85.3647),
            category = "Bihar",
            description = "Litchi City of India",
            keywords = listOf("muzaffarpur", "bihar")
        ),
        Destination(
            id = "loc_bhagalpur",
            name = "Bhagalpur",
            location = GeoPoint(25.2425, 86.9842),
            category = "Bihar",
            description = "Silk City of India",
            keywords = listOf("bhagalpur", "bihar")
        ),
        Destination(
            id = "loc_ranchi",
            name = "Ranchi",
            location = GeoPoint(23.3441, 85.3096),
            category = "Jharkhand",
            description = "City of Waterfalls & Capital of Jharkhand",
            keywords = listOf("ranchi", "jharkhand")
        ),
        Destination(
            id = "loc_jamshedpur",
            name = "Jamshedpur",
            location = GeoPoint(22.8046, 86.2029),
            category = "Jharkhand",
            description = "Steel City of India (Tata)",
            keywords = listOf("jamshedpur", "tatanagar", "jharkhand")
        ),
        Destination(
            id = "loc_dhanbad",
            name = "Dhanbad",
            location = GeoPoint(23.7957, 86.4304),
            category = "Jharkhand",
            description = "Coal Capital of India",
            keywords = listOf("dhanbad", "jharkhand")
        ),
        Destination(
            id = "loc_kolkata",
            name = "Kolkata",
            location = GeoPoint(22.5726, 88.3639),
            category = "West Bengal",
            description = "City of Joy & Howrah Bridge",
            keywords = listOf("kolkata", "calcutta", "wb")
        ),
        Destination(
            id = "loc_siliguri",
            name = "Siliguri",
            location = GeoPoint(26.7271, 88.3953),
            category = "West Bengal",
            description = "Gateway to Northeast & Darjeeling",
            keywords = listOf("siliguri", "wb")
        ),
        Destination(
            id = "loc_darjeeling",
            name = "Darjeeling",
            location = GeoPoint(27.041, 88.2663),
            category = "West Bengal",
            description = "Tea Gardens & Himalayan Railway",
            keywords = listOf("darjeeling", "wb")
        ),
        Destination(
            id = "loc_bhubaneswar",
            name = "Bhubaneswar",
            location = GeoPoint(20.2961, 85.8245),
            category = "Odisha",
            description = "Temple City of India & Capital of Odisha",
            keywords = listOf("bhubaneswar", "odisha")
        ),
        Destination(
            id = "loc_puri",
            name = "Puri",
            location = GeoPoint(19.8135, 85.8312),
            category = "Odisha",
            description = "Jagannath Temple & Golden Beach",
            keywords = listOf("puri", "odisha")
        ),
        Destination(
            id = "loc_cuttack",
            name = "Cuttack",
            location = GeoPoint(20.4625, 85.8828),
            category = "Odisha",
            description = "Silver City & Millennium City",
            keywords = listOf("cuttack", "odisha")
        ),
        Destination(
            id = "loc_guwahati",
            name = "Guwahati",
            location = GeoPoint(26.1445, 91.7362),
            category = "Assam",
            description = "Gateway to Northeast & Kamakhya Temple",
            keywords = listOf("guwahati", "assam")
        ),
        Destination(
            id = "loc_bengaluru",
            name = "Bengaluru",
            location = GeoPoint(12.9716, 77.5946),
            category = "Karnataka",
            description = "Silicon Valley of India",
            keywords = listOf("bengaluru", "bangalore", "karnataka")
        ),
        Destination(
            id = "loc_mysuru",
            name = "Mysuru",
            location = GeoPoint(12.2958, 76.6394),
            category = "Karnataka",
            description = "Heritage City & Mysore Palace",
            keywords = listOf("mysuru", "mysore", "karnataka")
        ),
        Destination(
            id = "loc_mangaluru",
            name = "Mangaluru",
            location = GeoPoint(12.9141, 74.856),
            category = "Karnataka",
            description = "Coastal Port City",
            keywords = listOf("mangaluru", "mangalore", "karnataka")
        ),
        Destination(
            id = "loc_hyderabad",
            name = "Hyderabad",
            location = GeoPoint(17.385, 78.4867),
            category = "Telangana",
            description = "City of Pearls & Cyberabad",
            keywords = listOf("hyderabad", "charminar", "telangana")
        ),
        Destination(
            id = "loc_warangal",
            name = "Warangal",
            location = GeoPoint(17.9689, 79.5941),
            category = "Telangana",
            description = "Kakatiya Heritage City",
            keywords = listOf("warangal", "telangana")
        ),
        Destination(
            id = "loc_chennai",
            name = "Chennai",
            location = GeoPoint(13.0827, 80.2707),
            category = "Tamil Nadu",
            description = "Detroit of India & Marina Beach",
            keywords = listOf("chennai", "madras", "tamil nadu")
        ),
        Destination(
            id = "loc_coimbatore",
            name = "Coimbatore",
            location = GeoPoint(11.0168, 76.9558),
            category = "Tamil Nadu",
            description = "Manchester of South India",
            keywords = listOf("coimbatore", "tamil nadu")
        ),
        Destination(
            id = "loc_madurai",
            name = "Madurai",
            location = GeoPoint(9.9252, 78.1198),
            category = "Tamil Nadu",
            description = "Meenakshi Amman Temple",
            keywords = listOf("madurai", "tamil nadu")
        ),
        Destination(
            id = "loc_kochi",
            name = "Kochi",
            location = GeoPoint(9.9312, 76.2673),
            category = "Kerala",
            description = "Queen of Arabian Sea & Port City",
            keywords = listOf("kochi", "cochin", "kerala")
        ),
        Destination(
            id = "loc_thiruvananthapuram",
            name = "Thiruvananthapuram",
            location = GeoPoint(8.5241, 76.9366),
            category = "Kerala",
            description = "Padmanabhaswamy Temple & Capital",
            keywords = listOf("thiruvananthapuram", "trivandrum", "kerala")
        ),
        Destination(
            id = "loc_kozhikode",
            name = "Kozhikode",
            location = GeoPoint(11.2588, 75.7804),
            category = "Kerala",
            description = "City of Spices & Calicut Beach",
            keywords = listOf("kozhikode", "calicut", "kerala")
        ),
        Destination(
            id = "loc_visakhapatnam",
            name = "Visakhapatnam",
            location = GeoPoint(17.6868, 83.2185),
            category = "Andhra Pradesh",
            description = "City of Destiny & Naval Command",
            keywords = listOf("visakhapatnam", "vizag", "ap")
        ),
        Destination(
            id = "loc_vijayawada",
            name = "Vijayawada",
            location = GeoPoint(16.5062, 80.648),
            category = "Andhra Pradesh",
            description = "Kanaka Durga Temple & Commercial Hub",
            keywords = listOf("vijayawada", "ap")
        ),
        Destination(
            id = "loc_tirupati",
            name = "Tirupati",
            location = GeoPoint(13.6288, 79.4192),
            category = "Andhra Pradesh",
            description = "Lord Venkateswara Balaji Temple",
            keywords = listOf("tirupati", "balaji", "ap")
        ),
        Destination(
            id = "loc_goa__panaji",
            name = "Goa (Panaji)",
            location = GeoPoint(15.4909, 73.8278),
            category = "Goa",
            description = "Beaches & Heritage Churches",
            keywords = listOf("goa", "panaji")
        ),
    )
}
