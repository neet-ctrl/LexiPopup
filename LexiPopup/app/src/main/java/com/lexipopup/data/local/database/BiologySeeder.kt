package com.lexipopup.data.local.database

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Seeds the local database with ~65 high-quality NEET UG / NEET PG biology terms.
 * Topics span Cell Biology, Genetics, Molecular Biology, Plant Physiology, Human
 * Physiology, Ecology, Evolution, Biotechnology, and Immunology.
 *
 * All rows are inserted with mode = 'biology' and source = 'seed'.
 * Uses INSERT OR IGNORE — safe to call multiple times; existing rows are skipped.
 */
object BiologySeeder {

    private data class BioTerm(
        val word: String,
        val pronunciation: String,
        val partOfSpeech: String,
        val shortMeaning: String,
        val detailedMeaning: String,
        val hindiMeaning: String,
        val exampleSentence: String,
        val synonyms: String,
        val etymology: String,
        val difficultyLevel: Int,
        val frequencyRating: Int
    )

    private val terms = listOf(

        // ── Cell Biology ──────────────────────────────────────────────────────

        BioTerm("mitochondria", "/ˌmaɪtəˈkɒndriə/", "noun",
            "Organelles that generate ATP via cellular respiration; the 'powerhouse of the cell'.",
            "Double-membrane organelles found in eukaryotic cells. They house the Krebs cycle and oxidative phosphorylation. Have their own circular DNA (mtDNA) and 70S ribosomes, supporting the endosymbiotic theory. Cristae (inner membrane folds) maximise surface area for ATP synthesis. Key NEET topic: structure of inner and outer membranes, role of matrix and cristae.",
            "माइटोकॉन्ड्रिया — कोशिका का ऊर्जा केंद्र",
            "Mutations in mitochondria can disrupt ATP production and cause metabolic diseases.",
            "chondriosome, powerhouse organelle",
            "Greek: mitos (thread) + khondrion (granule)",
            8, 95),

        BioTerm("chloroplast", "/ˈklɒrəˌplɑːst/", "noun",
            "Plant organelle that performs photosynthesis; contains chlorophyll.",
            "Double-membrane plastid found in plant cells and algae. Inner membrane forms thylakoids arranged in grana. Light reactions occur in thylakoid membranes; Calvin cycle occurs in stroma. Contain their own DNA and 70S ribosomes (endosymbiotic origin). Chlorophyll a and b absorb red and blue light. NEET focuses on Z-scheme, photophosphorylation, and ETC.",
            "क्लोरोप्लास्ट — प्रकाश संश्लेषण का स्थान",
            "The Calvin cycle occurs in the stroma of the chloroplast, producing G3P from CO₂.",
            "plastid, chromatophore",
            "Greek: chloros (green) + plastos (formed/moulded)",
            7, 90),

        BioTerm("ribosome", "/ˈraɪbəˌsoʊm/", "noun",
            "Non-membranous organelle that synthesises proteins by translating mRNA.",
            "Composed of rRNA and proteins. Prokaryotes have 70S ribosomes (50S + 30S); eukaryotes have 80S (60S + 40S). Mitochondria and chloroplasts retain 70S ribosomes. Ribosomes may be free in cytoplasm (cytosolic proteins) or bound to RER (secretory/membrane proteins). Antibiotics like streptomycin target 30S; erythromycin targets 50S — important NEET PG pharmacology fact.",
            "राइबोसोम — प्रोटीन संश्लेषण का स्थान",
            "Polysomes consist of multiple ribosomes simultaneously translating the same mRNA strand.",
            "polysome complex",
            "Ribose (nucleic acid sugar) + Greek soma (body)",
            6, 92),

        BioTerm("endoplasmic reticulum", "/ˌɛndəˈplæzmɪk rɪˈtɪkjʊləm/", "noun",
            "Network of membranes in eukaryotes; RER synthesises proteins, SER lipids and detoxification.",
            "Rough ER (RER) is studded with ribosomes and involved in protein folding, glycosylation, and quality control (via BiP chaperone). Smooth ER (SER) lacks ribosomes; synthesises phospholipids, cholesterol, steroid hormones, and detoxifies drugs (cytochrome P450 in liver). RER is continuous with the outer nuclear envelope. NEET PG: SER hyperplasia occurs in patients on chronic phenobarbital.",
            "अंतर्द्रव्यी जालिका — कोशिकांगों का नेटवर्क",
            "Liver cells have abundant SER for detoxifying drugs and synthesising bile acids.",
            "ER, reticulum",
            "Greek: endon (within) + plasma (form) + Latin reticulum (little net)",
            7, 80),

        BioTerm("lysosome", "/ˈlaɪsəˌsoʊm/", "noun",
            "Membrane-bound organelle containing hydrolytic enzymes for intracellular digestion.",
            "Called 'suicidal bags of the cell' (de Duve). Contain over 50 acid hydrolases active at pH 5. Functions: autophagy (self-digestion of old organelles), heterophagy (digestion of engulfed material), apoptosis. Lysosomal storage diseases (Gaucher, Tay-Sachs, Pompe) result from enzyme deficiencies. NEET PG: important in pathology of storage disorders.",
            "लाइसोसोम — कोशिका की सफाई प्रणाली",
            "In Gaucher disease, lysosomes accumulate glucocerebrosides due to deficient glucocerebrosidase.",
            "digestive vacuole, secondary lysosome",
            "Greek: lysis (loosening/dissolution) + soma (body)",
            7, 82),

        BioTerm("golgi apparatus", "/ˈɡɒldʒi æpəˈreɪtəs/", "noun",
            "Stack of membrane cisternae that processes and packages proteins for secretion.",
            "Composed of cis, medial, and trans cisternae. Proteins arrive from RER in vesicles and undergo further glycosylation, phosphorylation, and sulfation. Trans-Golgi network (TGN) sorts proteins to lysosomes, plasma membrane, or secretory vesicles. Plant cells have dictyosomes (Golgi equivalents) that also synthesise cell wall polysaccharides. Discovered by Camillo Golgi (1898).",
            "गॉल्गी उपकरण — प्रोटीन पैकेजिंग केंद्र",
            "Mucin secreted by goblet cells is packaged and glycosylated in the Golgi apparatus.",
            "Golgi complex, dictyosome",
            "Named after Camillo Golgi; Latin apparatus (preparation)",
            6, 78),

        BioTerm("centriole", "/ˈsɛntrɪˌoʊl/", "noun",
            "Cylindrical organelle composed of 9 triplet microtubules; forms the mitotic spindle.",
            "Found in animal cells and lower plant cells; absent in higher plants and fungi. Pair of centrioles = centrosome (MTOC). During cell division, centrioles duplicate and migrate to poles, nucleating the spindle. Also form basal bodies of cilia and flagella (9+2 axoneme structure). NEET: 9+0 arrangement in centriole vs 9+2 in cilia.",
            "सेंट्रिओल — कोशिका विभाजन में स्पिंडल निर्माणकर्ता",
            "The 9+0 microtubule arrangement in centrioles contrasts with the 9+2 pattern of cilia.",
            "basal body",
            "Latin centrum (centre) + diminutive -ole",
            8, 74),

        // ── Genetics ─────────────────────────────────────────────────────────

        BioTerm("allele", "/əˈliːl/", "noun",
            "Alternative form of a gene at a specific locus on a chromosome.",
            "Each diploid organism carries two alleles per locus (one from each parent). Dominant alleles mask recessive ones. Multiple allelism: ABO blood group is controlled by three alleles (Iᴬ, Iᴮ, i). Co-dominant alleles are both expressed (AB blood type). NEET frequently tests monohybrid crosses, dihybrid crosses, and multiple allele problems.",
            "एलील — जीन का वैकल्पिक रूप",
            "The ABO blood group locus has three alleles: Iᴬ, Iᴮ, and i.",
            "allelomorph, gene variant",
            "Greek: allelōn (of one another)",
            5, 88),

        BioTerm("epistasis", "/ɪˈpɪstəsɪs/", "noun",
            "Interaction where one gene (epistatic) masks the expression of another (hypostatic).",
            "Types tested in NEET: Dominant epistasis (12:3:1 ratio), Recessive epistasis (9:3:4), Duplicate dominant (15:1), Duplicate recessive (9:7), Dominant suppressor (13:3). The Bombay phenotype (hh genotype) suppresses ABO expression — the H gene is hypostatic. Distinguish from simple dominance: epistasis involves non-allelic genes.",
            "एपिस्टेसिस — जीन-जीन अन्योन्यक्रिया",
            "Bombay phenotype results from homozygous recessive hh, suppressing ABO antigen expression.",
            "gene masking, gene interaction",
            "Greek: epi (upon) + stasis (standing) — one gene 'standing upon' another",
            9, 70),

        BioTerm("pleiotropy", "/ˈplaɪətrəpi/", "noun",
            "Single gene producing multiple, seemingly unrelated phenotypic effects.",
            "Classic NEET example: PKU (phenylalanine hydroxylase gene) causes intellectual disability, fair skin, musty odour, and seizures via accumulation of phenylalanine. Sickle-cell anaemia (HbS mutation) affects RBCs, spleen, kidneys, brain, and joints. Marfan syndrome (FBN1 gene): tall stature, arachnodactyly, lens dislocation, aortic aneurysm. NEET PG: useful for clinical correlations.",
            "प्लियोट्रोपी — एक जीन, अनेक प्रभाव",
            "Sickle-cell disease is pleiotropic — one mutation in β-globin causes anaemia, organ damage, and pain crises.",
            "polygenic effects",
            "Greek: pleio (many) + tropos (turning/affecting)",
            8, 72),

        BioTerm("crossing over", "/ˈkrɒsɪŋ ˈoʊvər/", "noun",
            "Exchange of segments between non-sister chromatids of homologous chromosomes during meiosis I.",
            "Occurs during pachytene of prophase I at chiasmata. Generates recombinant chromosomes, increasing genetic diversity. Map distance (Morgan units) = recombination frequency × 100. Genes far apart show ~50% recombination (appear unlinked). Hotspots: double-strand breaks initiated by SPO11 enzyme. NEET: calculate recombination frequency from test-cross ratios.",
            "क्रॉसिंग ओवर — आनुवंशिक पुनर्संयोजन",
            "A recombination frequency of 20% between two genes means they are 20 cM (centimorgans) apart.",
            "recombination, chiasma formation",
            "English: crossing (from cross) + over; coined by Thomas Hunt Morgan",
            8, 75),

        BioTerm("codominance", "/koʊˈdɒmɪnəns/", "noun",
            "Both alleles are fully and simultaneously expressed in the heterozygous phenotype.",
            "Classic example: ABO blood type — Iᴬ and Iᴮ are codominant (genotype IᴬIᴮ → blood type AB). Sickle-cell trait (HbA/HbS) shows codominance: both normal and sickle haemoglobin present. Distinguish from incomplete dominance (blending) — in codominance both alleles express their full traits without blending. NEET often confuses codominance with incomplete dominance.",
            "सहप्रभाविता — दोनों एलील की पूर्ण अभिव्यक्ति",
            "In AB blood type, both A and B antigens are present on the RBC surface due to codominance.",
            "co-expression, dual dominance",
            "Latin: co (together) + dominare (to rule)",
            6, 80),

        // ── Molecular Biology ─────────────────────────────────────────────────

        BioTerm("operon", "/ˈɒpərɒn/", "noun",
            "Prokaryotic gene regulatory unit: a group of structural genes under one promoter and operator.",
            "Lac operon (Jacob-Monod model, 1961): structural genes lacZ, lacA, lacY; repressor encoded by lacI; inducer is allolactose. Inducible operon: repressor-ON by default; inducer removes repressor. Trp operon (repressible): repressor-OFF by default; tryptophan (corepressor) activates repressor. NEET PG: understand positive and negative regulation, catabolite repression (CAP-cAMP).",
            "ओपेरॉन — प्रोकैरियोटिक जीन नियंत्रण इकाई",
            "In the lac operon, allolactose acts as an inducer by binding the lac repressor, allowing transcription.",
            "regulon, gene cluster",
            "Latin: opera (works) — a unit that 'works together'",
            8, 78),

        BioTerm("restriction enzyme", "/rɪˈstrɪkʃən ˈɛnzaɪm/", "noun",
            "Endonuclease that cuts double-stranded DNA at specific palindromic recognition sequences.",
            "Type II REs are used in genetic engineering. EcoRI cuts at 5'-GAATTC-3', creating sticky ends; SmaI cuts at CCCGGG, creating blunt ends. Named by organism: EcoRI from E. coli R strain. Sticky ends allow efficient ligation; blunt ends require T4 DNA ligase. Discovered by Arber, Smith, Nathans (Nobel 1978). NEET: recognition site, cutting pattern, sticky vs blunt ends.",
            "प्रतिबंध एंजाइम — आणविक कैंची",
            "EcoRI recognises the palindromic sequence 5'-GAATTC-3' and cuts between G and A on each strand.",
            "endonuclease, molecular scissors",
            "Latin: restrictio (limitation) — enzymes that 'restrict' bacteriophage DNA",
            7, 82),

        BioTerm("polymerase chain reaction", "/pəˈlɪmərɪs tʃeɪn rɪˈækʃən/", "noun",
            "In vitro technique for exponentially amplifying a specific DNA sequence using repeated cycles.",
            "Three steps per cycle: (1) Denaturation at 94°C (DNA strands separate); (2) Annealing at 50-65°C (primers bind); (3) Extension at 72°C (Taq polymerase extends). Template DNA doubles with each cycle → 2ⁿ copies after n cycles. Taq polymerase (from Thermus aquaticus) is thermostable. Invented by Kary Mullis, Nobel 1993. Applications: DNA fingerprinting, diagnosis, forensics, RT-PCR for RNA detection.",
            "पीसीआर — डीएनए प्रवर्धन तकनीक",
            "PCR can amplify a single copy of a gene to billions of copies in just a few hours.",
            "PCR, DNA amplification, thermal cycling",
            "Greek: poly (many) + meros (parts); chain (sequential cycles)",
            7, 88),

        BioTerm("CRISPR-Cas9", "/ˈkrɪspər ˌkæsˈnaɪn/", "noun",
            "Bacterial adaptive immune system adapted as a precise genome-editing tool.",
            "CRISPR (Clustered Regularly Interspaced Short Palindromic Repeats) spacers encode memory of past viral infections. Cas9 nuclease guided by a single guide RNA (sgRNA) cuts target DNA at a complementary sequence adjacent to a PAM (5'-NGG-3'). Cuts create DSBs repaired by NHEJ (creating indels/knockouts) or HDR (precise edits). Nobel Prize 2020: Doudna and Charpentier. NEET PG: applications in gene therapy, agriculture, disease modelling.",
            "क्रिस्पर-कैस9 — जीनोम संपादन उपकरण",
            "CRISPR-Cas9 can correct the HBB gene mutation in stem cells to treat sickle-cell disease.",
            "gene editing, genome scissors",
            "CRISPR: Clustered Regularly Interspaced Short Palindromic Repeats; Cas: CRISPR-associated",
            9, 76),

        BioTerm("transgenic organism", "/trænzˈdʒɛnɪk ˈɔːrɡənɪzəm/", "noun",
            "Organism whose genome has been stably integrated with a gene from another species.",
            "Production: isolate gene → clone into vector (plasmid/virus/Agrobacterium) → introduce into host. Methods: microinjection (animals), Agrobacterium tumefaciens Ti plasmid (plants), biolistics (gene gun). Examples: Bt cotton (Cry gene from Bacillus thuringiensis), Bt brinjal, Flavr Savr tomato (delayed ripening), Rosie cow (human lactoferrin in milk), Golden Rice (β-carotene). Biosafety concerns: horizontal gene transfer, ecological impact.",
            "ट्रांसजेनिक जीव — विदेशी जीन वाला जीव",
            "Bt cotton produces Cry1Ac insecticidal protein, making it resistant to bollworm.",
            "GMO, genetically modified organism",
            "Latin: trans (across) + Greek genikos (of origin/race)",
            7, 80),

        // ── Plant Physiology ──────────────────────────────────────────────────

        BioTerm("photoperiodism", "/ˌfoʊtoʊˈpɪərɪəˌdɪzəm/", "noun",
            "Plant's response to the relative lengths of day (light) and night (dark) for flowering.",
            "Long-day plants (LDP): flower when night < critical length (e.g., wheat, radish). Short-day plants (SDP): flower when night > critical length (e.g., chrysanthemum, tobacco). Day-neutral plants: unaffected (e.g., tomato, rose). Phytochrome pigment (Pr ⇌ Pfr) mediates photoperiodism. Night interruption by brief red light converts Pfr and prevents SDP flowering. NEET: critical night length matters, not day length.",
            "प्रकाश-आवधिकता — पुष्पन पर प्रकाश प्रभाव",
            "Chrysanthemum, a short-day plant, requires long uninterrupted dark periods to initiate flowering.",
            "photoinduction, light-period response",
            "Greek: phos/photos (light) + Latin periodicus (recurring) + Greek ismos (condition)",
            8, 72),

        BioTerm("transpiration", "/ˌtrænsˌpɪˈreɪʃən/", "noun",
            "Loss of water vapour from plant surfaces, mainly through stomata; drives water uptake.",
            "Types: stomatal (90%), cuticular (5-7%), lenticular (<1%). Transpiration pull (cohesion-tension theory): water loss creates tension that pulls water column up through xylem. Stomatal opening: guard cells absorb water → become turgid → stoma opens. Potassium pump and malate accumulation drive turgor. Antiperpirants (ABA) close stomata. NEET: factors affecting transpiration rate.",
            "वाष्पोत्सर्जन — पौधों से जल हानि",
            "The transpiration stream carries dissolved minerals from roots to leaves via the xylem.",
            "evapotranspiration, water loss",
            "Latin: trans (through) + spirare (to breathe)",
            6, 78),

        BioTerm("auxin", "/ˈɔːksɪn/", "noun",
            "Plant hormone (IAA) that promotes cell elongation and mediates tropic responses.",
            "Indole-3-acetic acid (IAA) is the main natural auxin. Synthesised in shoot apex from tryptophan. Polar auxin transport (PAT) is unidirectional — basipetal in shoots. High concentrations inhibit root growth (explains phototropism and gravitropism differential effects). Apical dominance: high auxin from apex suppresses lateral buds. Commercial uses: 2,4-D (herbicide), NAA and IBA (rooting). NEET: Went's experiment, Avena curvature test.",
            "ऑक्सिन — वृद्धि हॉर्मोन",
            "Uneven auxin distribution toward the shaded side causes phototropic bending in shoots.",
            "IAA, indole-3-acetic acid",
            "Greek: auxein (to grow/increase)",
            7, 82),

        BioTerm("vernalization", "/ˌvɜːrnəlaɪˈzeɪʃən/", "noun",
            "Induction of flowering by prolonged cold exposure; promotes competence to flower in spring.",
            "Many biennial plants (carrot, beet) and winter cereals (winter wheat, rye) require vernalization. Cold is perceived by shoot apical meristem. Epigenetic mechanism: cold triggers Polycomb-mediated repression of FLC (Flowering Locus C), a floral repressor. FLC repression is maintained through mitosis (epigenetic memory). NEET: Lysenko's experiments; distinguish from stratification (seed cold requirement).",
            "वर्नलाइज़ेशन — ठंड से पुष्पन प्रेरण",
            "Winter wheat varieties must experience cold vernalization before they will flower in spring.",
            "cold treatment, jarovization",
            "Latin: vernalis (of spring) — mimics spring onset after winter",
            8, 65),

        BioTerm("Calvin cycle", "/ˈkælvɪn ˈsaɪkəl/", "noun",
            "Light-independent (dark) reactions of photosynthesis that fix CO₂ into organic molecules.",
            "Also called C3 pathway. Three stages: (1) Carbon fixation: CO₂ + RuBP → 2 × 3-PGA (catalysed by RuBisCO); (2) Reduction: 3-PGA → G3P using ATP and NADPH; (3) Regeneration: RuBP regenerated from G3P using ATP. Net: 3 CO₂ → 1 G3P (net). RuBisCO also catalyses oxygenation (photorespiration). C4 plants concentrate CO₂ around RuBisCO to minimise photorespiration.",
            "केल्विन चक्र — कार्बन स्थिरीकरण",
            "RuBisCO is the key enzyme in the Calvin cycle, fixing atmospheric CO₂ into 3-PGA.",
            "dark reaction, C3 pathway, carbon fixation cycle",
            "Named after Melvin Calvin (Nobel 1961)",
            7, 85),

        // ── Human Physiology ──────────────────────────────────────────────────

        BioTerm("action potential", "/ˈækʃən pəˈtɛnʃəl/", "noun",
            "Rapid, self-propagating electrical signal in neurons and muscle cells; the nerve impulse.",
            "Resting membrane potential: −70 mV (inside negative). Phases: (1) Depolarisation — Na⁺ channels open, Na⁺ influx, membrane reaches +30 mV; (2) Repolarisation — K⁺ channels open, K⁺ efflux; (3) Hyperpolarisation — overshoot below −70 mV; (4) Return to rest via Na⁺/K⁺ ATPase. All-or-none law. Refractory period prevents backward propagation. Myelinated fibres: saltatory conduction; faster speed.",
            "एक्शन पोटेंशियल — तंत्रिका आवेग",
            "Tetrodotoxin (puffer fish toxin) blocks voltage-gated Na⁺ channels, preventing action potentials.",
            "nerve impulse, spike potential",
            "Latin: actio (action) + potentia (power)",
            8, 86),

        BioTerm("synapse", "/ˈsɪnæps/", "noun",
            "Junction between two neurons or a neuron and effector cell where signals are transmitted.",
            "Chemical synapse (most common): presynaptic terminal releases neurotransmitter (NT) into synaptic cleft → NT binds postsynaptic receptors. Types by NT: cholinergic (ACh), adrenergic (noradrenaline). Electrical synapses (gap junctions): fast, bidirectional. EPSP vs IPSP. Reuptake or enzymatic degradation terminates NT action. Botulinum toxin blocks ACh release. NEET PG: drugs targeting synaptic transmission.",
            "सिनैप्स — तंत्रिका जंक्शन",
            "At the neuromuscular junction, acetylcholine is released into the synaptic cleft to stimulate muscle contraction.",
            "synaptic junction, neural junction",
            "Greek: synapsis (connection/fastening)",
            7, 84),

        BioTerm("nephron", "/ˈnɛfrɒn/", "noun",
            "Structural and functional unit of the kidney responsible for filtration, reabsorption, and secretion.",
            "~1 million nephrons per kidney. Structure: Bowman's capsule + glomerulus (renal corpuscle) → PCT → Loop of Henle → DCT → collecting duct. Cortical nephrons (85%): short loops; juxtamedullary nephrons (15%): long loops, important for concentration. GFR ~125 mL/min (180 L/day) → 1.5 L urine/day. Countercurrent mechanism concentrates urine. NEET: clearance calculations, selective reabsorption at each segment.",
            "नेफ्रॉन — वृक्क की कार्यात्मक इकाई",
            "Glucose is completely reabsorbed in the proximal convoluted tubule of the nephron under normal conditions.",
            "renal tubule, kidney unit",
            "Greek: nephros (kidney) + -on (unit)",
            7, 87),

        BioTerm("glomerulus", "/ɡlɒˈmɛrjʊləs/", "noun",
            "Tuft of capillaries inside Bowman's capsule where blood is ultra-filtered to form filtrate.",
            "High hydrostatic pressure (~55 mmHg) drives filtration. Filtration barrier: fenestrated endothelium + GBM (type IV collagen) + podocyte foot processes (slit diaphragm with nephrin). Filtered: water, ions, glucose, amino acids, urea, creatinine. Not filtered: proteins, RBCs. Net filtration pressure = HP − (OP + CHP). GFR regulated by afferent/efferent arteriole tone (RAAS, myogenic reflex). NEET PG: diabetic nephropathy — GBM thickening.",
            "ग्लोमेरुलस — वृक्क का फ़िल्टर",
            "In nephrotic syndrome, damaged glomeruli allow proteins to pass into the filtrate, causing proteinuria.",
            "renal corpuscle tuft",
            "Latin: glomerulus (small ball of thread) — the capillary 'ball'",
            8, 80),

        BioTerm("haemoglobin", "/ˈhiːməˌɡloʊbɪn/", "noun",
            "Tetrameric iron-containing protein in RBCs that transports O₂ and CO₂.",
            "Structure: 2α + 2β chains, each with a haem group (Fe²⁺ + porphyrin ring). Sigmoidal O₂-dissociation curve (cooperative binding). Bohr effect: ↑CO₂/H⁺ → right shift (more O₂ released). 2,3-BPG stabilises deoxy-Hb → right shift. HbF has higher O₂ affinity than HbA (no BPG binding). CO binds Hb with 250× affinity of O₂ → carboxyhaemoglobin. NEET: variants — HbA, HbA₂, HbF, HbS (sickle), HbC.",
            "हीमोग्लोबिन — ऑक्सीजन वाहक",
            "The sigmoidal shape of the haemoglobin-O₂ dissociation curve reflects cooperative binding.",
            "Hb, oxygen carrier, erythrocyte pigment",
            "Greek: haima (blood) + Latin globus (sphere) + English -in (protein suffix)",
            7, 90),

        BioTerm("insulin", "/ˈɪnsjʊlɪn/", "noun",
            "Anabolic peptide hormone from β-cells of islets of Langerhans that lowers blood glucose.",
            "51-amino-acid hormone (A and B chains linked by disulfide bonds). Secreted in response to high blood glucose, amino acids, GIP, GLP-1. Mechanism: binds receptor tyrosine kinase → GLUT-4 translocation in muscle and fat → glucose uptake; inhibits gluconeogenesis, glycogenolysis; promotes glycogen synthesis, lipogenesis, protein synthesis. Absent/deficient in Type 1 DM (autoimmune β-cell destruction). First recombinant protein therapeutic (human insulin via E. coli, 1982).",
            "इन्सुलिन — रक्त शर्करा नियामक",
            "Insulin promotes GLUT-4 translocation to the cell membrane, enabling glucose uptake in muscle cells.",
            "hypoglycaemic hormone, anabolic hormone",
            "Latin: insula (island) — from the islets of Langerhans",
            7, 88),

        BioTerm("thyroxine", "/θaɪˈrɒksɪn/", "noun",
            "Iodine-containing thyroid hormone (T4) that regulates basal metabolic rate.",
            "T4 (thyroxine) and T3 (triiodothyronine) synthesised from thyroglobulin by thyroid follicular cells. Requires iodine (dietary). TSH (pituitary) stimulates synthesis via cAMP. T4 converted to active T3 in peripheral tissues by deiodinases. Increases BMR, heart rate, oxygen consumption, growth, neurological development. Deficiency: hypothyroidism (goitre, myxoedema, cretinism in children). Excess: hyperthyroidism (Graves' disease, exophthalmos). NEET PG: thyroid function tests.",
            "थायरॉक्सिन — चयापचय नियामक हॉर्मोन",
            "Iodine deficiency impairs thyroxine synthesis, leading to goitre and hypothyroidism.",
            "T4, tetraiodothyronine",
            "Greek: thyreos (shield, for thyroid cartilage shape) + oxys (sharp) + -ine",
            7, 82),

        // ── Ecology ───────────────────────────────────────────────────────────

        BioTerm("ecological succession", "/ˌiːkəˈlɒdʒɪkəl səkˈsɛʃən/", "noun",
            "Sequential replacement of communities in an area over time, from pioneer to climax.",
            "Primary succession: on bare substrate (rock, lava) with no soil. Hydrosere (aquatic), Xerosere (dry rock). Pioneer species (lichens, mosses) modify environment. Secondary succession: disturbed area with existing soil (faster). Seral communities → climax community (stable). NEET: Hydrarch (pond → forest) vs Xerarch (rock → forest) succession stages; concept of facilitation, tolerance, inhibition models.",
            "पारिस्थितिक अनुक्रमण — समुदाय परिवर्तन",
            "Lichens act as pioneer species in xerarch succession by slowly weathering bare rock into soil.",
            "biotic succession, seral progression",
            "Greek: oikos (house) + logos (study); Latin successio (following)",
            7, 75),

        BioTerm("nitrogen fixation", "/ˈnaɪtrədʒən fɪkˈseɪʃən/", "noun",
            "Biological conversion of atmospheric N₂ into ammonia (NH₃) by diazotrophs.",
            "Enzyme: nitrogenase complex (dinitrogenase + dinitrogenase reductase); contains Fe-Mo cofactor. Requires 16 ATP per N₂. Inactivated by O₂ (hence leghaemoglobin protects in root nodules). Organisms: Rhizobium (symbiotic), Azotobacter, Clostridium (free-living). Cyanobacteria (Anabaena, Nostoc) in heterocysts. NEET: nitrogen cycle — fixation → nitrification → denitrification → ammonification.",
            "नाइट्रोजन स्थिरीकरण — N₂ से NH₃ रूपांतरण",
            "Rhizobium bacteria in legume root nodules fix atmospheric N₂ using the nitrogenase complex.",
            "biological nitrogen fixation, diazotrophy",
            "Latin: nitrum (soda) + Greek: gen (producing); Latin fixare (to fasten)",
            8, 78),

        BioTerm("eutrophication", "/juːˌtrɒfɪˈkeɪʃən/", "noun",
            "Excessive nutrient enrichment of a water body, causing algal blooms and oxygen depletion.",
            "Key nutrients: nitrates and phosphates (from fertiliser runoff, sewage). Process: nutrients → algal/cyanobacterial blooms → algae die → decomposed by bacteria → BOD ↑ → dissolved O₂ ↓ (hypoxia) → fish kills. Cultural (anthropogenic) vs natural eutrophication. Indicator species: Eicchornia (water hyacinth) — world's fastest growing plant, monoculture destroys biodiversity. NEET: relate to water pollution, BOD, COD.",
            "सुपोषण — जलाशय में अत्यधिक पोषण",
            "Agricultural runoff containing phosphates and nitrates triggers eutrophication in lakes.",
            "hypereutrophication, lake fertilisation",
            "Greek: eu (well) + trophe (nourishment)",
            7, 72),

        BioTerm("biodiversity", "/ˌbaɪoʊdaɪˈvɜːrsɪti/", "noun",
            "Variety of life at genetic, species, and ecosystem levels in a given area.",
            "Three levels: (1) Genetic diversity (allelic variants within a species); (2) Species diversity (richness and evenness — Shannon index, Simpson index); (3) Ecosystem diversity. Hotspots (Norman Myers): areas with ≥1,500 endemic plant species and <30% of original habitat. India has 4 hotspots: Himalaya, Western Ghats + Sri Lanka, Indo-Burma, Sundaland. In-situ conservation: national parks, wildlife sanctuaries, biosphere reserves. Ex-situ: zoos, cryopreservation, seed banks.",
            "जैव विविधता — जीवन की विविधता",
            "India is one of 17 mega-diverse countries, harbouring about 7–8% of all recorded species.",
            "biological diversity, species richness",
            "Greek: bios (life) + Latin diversitas (variety)",
            6, 82),

        // ── Evolution ─────────────────────────────────────────────────────────

        BioTerm("Hardy-Weinberg equilibrium", "/ˈhɑːrdi ˈwaɪnbɜːrɡ ˌiːkwɪˈlɪbriəm/", "noun",
            "Principle that allele and genotype frequencies remain constant in a non-evolving population.",
            "Conditions: large population, random mating, no mutation, no gene flow, no natural selection. Equations: p + q = 1 (allele freq); p² + 2pq + q² = 1 (genotype freq). p = dominant allele, q = recessive allele, p² = homozygous dominant, 2pq = heterozygous, q² = homozygous recessive. Useful to calculate carrier frequency from disease prevalence. Deviations indicate evolution. NEET: solve numerical problems using q² = disease incidence.",
            "हार्डी-वाइनबर्ग साम्यावस्था — जनसंख्या आनुवंशिकी",
            "If q² = 1/10000 for albinism, then q = 0.01 and 2pq ≈ 0.02 (carrier frequency).",
            "population genetic equilibrium",
            "Named after G.H. Hardy (mathematician) and Wilhelm Weinberg (physician)",
            9, 74),

        BioTerm("adaptive radiation", "/əˈdæptɪv ˌreɪdiˈeɪʃən/", "noun",
            "Rapid diversification of one ancestral species into multiple ecologically different forms.",
            "Classic NEET examples: Darwin's finches (Galapagos — 14 species from one ancestor, different beaks for seeds, insects, cacti). Marsupials in Australia. Cichlid fish in African Rift lakes. Hawaiian honeycreepers. Requires ecological opportunity (vacant niches) + key innovation. Occurs rapidly after mass extinctions. Distinguished from convergent evolution (unrelated species → similar forms) and divergent evolution.",
            "अनुकूली विकिरण — एक पूर्वज, अनेक रूप",
            "Darwin's finches on the Galapagos Islands exemplify adaptive radiation from a common ancestral species.",
            "divergent evolution, cladogenesis",
            "Latin: adaptare (to fit) + radiare (to radiate outward)",
            7, 72),

        BioTerm("genetic drift", "/dʒəˈnɛtɪk drɪft/", "noun",
            "Random change in allele frequencies in a small population due to chance sampling events.",
            "Most pronounced in small populations. Effects: loss of heterozygosity, random fixation/loss of alleles. Founder effect: new population from small founder group (e.g., Amish — Ellis-van Creveld syndrome). Bottleneck effect: population crash then recovery (e.g., cheetahs, northern elephant seals). Unlike natural selection, genetic drift is random and not adaptive. Leads to inbreeding, reproductive isolation, ultimately speciation.",
            "आनुवंशिक विचलन — यादृच्छिक एलील परिवर्तन",
            "The Amish population shows high frequency of certain rare diseases due to a founder effect bottleneck.",
            "random genetic drift, Sewall Wright effect",
            "Greek: genetikos (of origin); Old Norse: drift (impulse/force)",
            8, 70),

        BioTerm("allopatric speciation", "/ˌæləˈpætrɪk ˌspeʃɪˈeɪʃən/", "noun",
            "Formation of new species due to geographic isolation preventing gene flow.",
            "Mechanism: population splits by physical barrier (mountain, river, sea) → isolated populations diverge due to natural selection, genetic drift, mutation → reproductive isolation evolves → cannot interbreed even if barrier removed. Examples: squirrels on either rim of the Grand Canyon; Galapagos finches. Sympatric speciation: within the same area (polyploidy in plants — more common). NEET: mechanisms of reproductive isolation (prezygotic vs postzygotic).",
            "एलोपेट्रिक स्पेशिएशन — भौगोलिक पृथक्करण से नई प्रजाति",
            "Galapagos finches underwent allopatric speciation as populations became isolated on different islands.",
            "geographic speciation, vicariance",
            "Greek: allos (other) + patra (homeland) + Latin species (kind)",
            8, 72),

        // ── Immunology ────────────────────────────────────────────────────────

        BioTerm("antigen", "/ˈæntɪdʒən/", "noun",
            "Foreign molecule (usually protein/polysaccharide) that elicits an immune response.",
            "Immunogenicity: ability to stimulate immune response (depends on molecular size, complexity, foreignness). Antigenicity: ability to bind antibody. Haptens: small molecules that are antigenic but not immunogenic alone (need carrier). Epitopes (antigenic determinants): specific portions recognised by antibodies or T-cell receptors. T-cell antigens must be processed and presented by MHC. B-cell antigens can be recognised directly (T-independent) or via T-helper cells.",
            "प्रतिजन — प्रतिरक्षा प्रतिक्रिया उत्प्रेरक",
            "Vaccines introduce antigens that prime the immune system without causing disease.",
            "immunogen, epitope",
            "Greek: anti (against) + genein (to produce)",
            6, 84),

        BioTerm("antibody", "/ˈæntɪˌbɒdi/", "noun",
            "Y-shaped immunoglobulin protein produced by plasma cells that specifically binds antigens.",
            "Structure: 2 heavy + 2 light chains (disulfide bonds). Variable (V) regions form antigen-binding site. Constant (C) region determines class (IgG, IgA, IgM, IgD, IgE). IgG: most abundant, crosses placenta. IgA: in secretions (gut, saliva, tears). IgM: first produced (pentamer, best complement activator). IgE: allergy/parasites. IgD: B-cell receptor. Monoclonal antibodies (hybridoma technology — Köhler and Milstein, Nobel 1984). NEET: isotype switching, opsonisation.",
            "एंटीबॉडी — प्रतिरक्षा प्रोटीन",
            "IgG antibodies cross the placental barrier to provide passive immunity to the foetus.",
            "immunoglobulin, Ig",
            "Greek: anti (against) + Old English bodig (body)",
            6, 85),

        BioTerm("phagocytosis", "/ˌfæɡəˈsaɪtəˌsɪs/", "noun",
            "Process by which cells engulf and digest large particles (bacteria, debris, dead cells).",
            "Carried out by professional phagocytes: macrophages, neutrophils, dendritic cells. Steps: (1) Chemotaxis; (2) Opsonisation (IgG, C3b coating); (3) Binding to Fc or complement receptors; (4) Engulfment → phagosome; (5) Fusion with lysosome → phagolysosome; (6) Killing (oxidative burst — superoxide, H₂O₂, HOCl via NADPH oxidase and myeloperoxidase). Chronic granulomatous disease: defective NADPH oxidase → recurrent infections.",
            "भक्षकणुता — कोशिका द्वारा कण भक्षण",
            "Macrophages use phagocytosis to destroy opsonised bacteria in infected tissue.",
            "endocytosis, cell eating",
            "Greek: phagein (to eat) + kytos (cell) + -osis (process)",
            7, 80),

        BioTerm("B lymphocyte", "/biː ˈlɪmfəˌsaɪt/", "noun",
            "Adaptive immune cell that differentiates into antibody-secreting plasma cells.",
            "Origin and maturation in bone marrow (Bursa of Fabricius in birds). Antigen-naive B-cells express IgM and IgD as BCR. Upon antigen activation (with T-helper help): proliferate → plasma cells (secrete antibodies) + memory B-cells. Class-switch recombination (CSR) changes Ig class under cytokine influence. Affinity maturation: somatic hypermutation in germinal centres selects high-affinity clones. NEET PG: X-linked agammaglobulinaemia (Bruton disease) — B-cell maturation defect.",
            "बी लिम्फोसाइट — एंटीबॉडी उत्पादक कोशिका",
            "B lymphocytes undergo clonal expansion after recognising their specific antigen, producing plasma cells.",
            "B cell, plasma cell precursor",
            "B from Bursa of Fabricius (birds) / Bone marrow (mammals); lymphocyte: Latin lympha (water) + Greek kytos (cell)",
            7, 78),

        BioTerm("T lymphocyte", "/tiː ˈlɪmfəˌsaɪt/", "noun",
            "Adaptive immune cell maturing in the thymus; mediates cell-mediated immunity.",
            "Origin: bone marrow → matures in thymus. Types: CD4⁺ helper T-cells (activate B-cells and macrophages via cytokines — IL-2, IFN-γ); CD8⁺ cytotoxic T-cells (kill virus-infected and tumour cells via perforin/granzyme); Regulatory T-cells (suppress immune response). MHC-I restricted (CD8⁺); MHC-II restricted (CD4⁺). HIV infects CD4⁺ T-cells. DiGeorge syndrome: thymic aplasia → T-cell deficiency. NEET PG: thymic selection (positive + negative).",
            "टी लिम्फोसाइट — कोशिका-मध्यस्थ प्रतिरक्षा",
            "CD4⁺ T-helper cells coordinate adaptive immunity by releasing cytokines that activate B-cells.",
            "T cell, thymic lymphocyte",
            "T from Thymus; lymphocyte: Latin lympha + Greek kytos",
            7, 80),

        // ── Reproduction & Development ─────────────────────────────────────────

        BioTerm("spermatogenesis", "/ˌspɜːrmətəˈdʒɛnɪsɪs/", "noun",
            "Process of sperm production from spermatogonia in the seminiferous tubules.",
            "Stages: Spermatogonium (2n) → Primary spermatocyte (2n, DNA doubled) → [Meiosis I] → Secondary spermatocytes (n) → [Meiosis II] → Spermatids (n) → [Spermiogenesis] → Spermatozoa. Location: seminiferous tubules supported by Sertoli cells (blood-testis barrier, ABP, inhibin). Leydig cells produce testosterone (LH-stimulated). FSH stimulates Sertoli cells. Temperature sensitive: 2-3°C below body temperature needed. NEET: contrast with oogenesis.",
            "शुक्राणुजनन — शुक्राणु निर्माण प्रक्रिया",
            "Sertoli cells provide structural support and nutrients to developing spermatocytes in the seminiferous tubules.",
            "sperm production, spermatogenesis",
            "Greek: sperma (seed) + genesis (origin/creation)",
            7, 76),

        BioTerm("oogenesis", "/ˌoʊəˈdʒɛnɪsɪs/", "noun",
            "Process of egg (ovum) production from oogonia; arrested twice during meiosis.",
            "Fetal: oogonia → primary oocytes (arrested in prophase I by birth). At puberty: monthly one primary oocyte resumes → secondary oocyte (arrested in metaphase II) + first polar body. After fertilisation: secondary oocyte completes meiosis II → ovum + second polar body. Net: 1 oogonium → 1 ovum + 3 polar bodies. NEET: contrast with spermatogenesis (spermatogonia remain throughout life; oocytes limited, fixed at birth). Folliculogenesis: primordial → primary → secondary → Graafian follicle.",
            "अण्डजनन — अण्डाणु निर्माण प्रक्रिया",
            "A secondary oocyte, arrested at metaphase II, only completes meiosis upon fertilisation.",
            "egg formation, ovogenesis",
            "Greek: oon (egg) + genesis (origin)",
            7, 75),

        BioTerm("embryogenesis", "/ˌɛmbriˈɒdʒənɪsɪs/", "noun",
            "Development of an embryo from the zygote through gastrulation to organogenesis.",
            "Cleavage: rapid mitotic divisions without cell growth → blastomeres → morula → blastula (blastocyst in mammals). Gastrulation: three germ layers — ectoderm, mesoderm, endoderm. Neurulation: neural plate → neural tube (ectoderm) — brain and spinal cord. Organogenesis: germ layers differentiate. Teratogens: drugs (thalidomide), infections (rubella), radiation cause malformations. NEET PG: neural tube defects (folic acid), embryological basis of congenital anomalies.",
            "भ्रूणजनन — भ्रूण विकास",
            "Gastrulation establishes the three primary germ layers — ectoderm, mesoderm, and endoderm.",
            "embryo development, ontogenesis",
            "Greek: embryon (unborn) + genesis (origin)",
            8, 72),

        // ── Biochemistry ──────────────────────────────────────────────────────

        BioTerm("enzyme kinetics", "/ˈɛnzaɪm kɪˈnɛtɪks/", "noun",
            "Study of enzyme reaction rates and the factors that affect them.",
            "Michaelis-Menten equation: V = Vmax[S] / (Km + [S]). Km: substrate concentration at half-Vmax; low Km = high affinity. Vmax: maximum reaction velocity. Lineweaver-Burk plot (double reciprocal): x-intercept = −1/Km; y-intercept = 1/Vmax. Competitive inhibition: same x-intercept (Km changes), y-intercept changes. Non-competitive: same x-intercept stays (Km unchanged), Vmax decreases. NEET PG: enzyme inhibitors as drugs (ACE inhibitors, statins, allopurinol).",
            "एंजाइम गतिकी — एंजाइम प्रतिक्रिया दर अध्ययन",
            "A low Km value indicates high enzyme affinity; a high Km suggests weaker substrate binding.",
            "Michaelis-Menten kinetics",
            "Greek: en (in) + zyme (leaven); Greek: kinetikos (moving)",
            9, 74),

        BioTerm("gluconeogenesis", "/ˌɡluːkəʊˌniːəˈdʒɛnɪsɪs/", "noun",
            "Synthesis of glucose from non-carbohydrate precursors (lactate, amino acids, glycerol).",
            "Primarily in liver (90%) and renal cortex. Precursors: lactate (Cori cycle), alanine (glucose-alanine cycle), glycerol (from triglyceride breakdown), oxaloacetate (from glucogenic amino acids). Key enzymes (reverse glycolysis): pyruvate carboxylase, PEPCK, fructose-1,6-bisphosphatase, glucose-6-phosphatase. Regulated by glucagon (+) and insulin (−). Fasting/starvation state: gluconeogenesis maintains blood glucose. NEET PG: metformin inhibits hepatic gluconeogenesis.",
            "ग्लूकोनियोजेनेसिस — नव ग्लूकोज संश्लेषण",
            "During prolonged fasting, gluconeogenesis in the liver converts amino acids and lactate into glucose.",
            "glucose synthesis, neoglucogenesis",
            "Greek: glykys (sweet) + neo (new) + genesis (creation)",
            8, 75),

        BioTerm("apoptosis", "/ˌæpəˈtoʊsɪs/", "noun",
            "Programmed cell death — an ordered, energy-dependent process essential for development and homeostasis.",
            "Intrinsic pathway: mitochondrial outer membrane permeabilisation (MOMP) → cytochrome c release → apoptosome → caspase-9 activation. Extrinsic: death ligands (FasL, TNF) bind receptors → caspase-8 activation. Both converge on executioner caspases-3,6,7. Morphology: cell shrinkage, chromatin condensation, DNA fragmentation (nucleosomal laddering on gel), apoptotic bodies. Anti-apoptotic: Bcl-2, Bcl-XL. Pro-apoptotic: Bax, Bak. NEET PG: Bcl-2 overexpression in B-cell lymphoma (t(14;18)).",
            "एपोप्टोसिस — क्रमादेशित कोशिका मृत्यु",
            "During embryonic development, apoptosis eliminates the webbing between developing fingers.",
            "programmed cell death, PCD",
            "Greek: apo (away from) + ptosis (falling) — like leaves falling from trees",
            8, 80),

        BioTerm("telomere", "/ˈtɛləˌmɪər/", "noun",
            "Repetitive nucleotide sequences (TTAGGG) at chromosome ends that protect genomic integrity.",
            "Telomeres prevent chromosome degradation, end-to-end fusion, and recognition as DSBs. Shorten with each mitotic division (end-replication problem of DNA polymerase). Critically short telomeres → replicative senescence (Hayflick limit) or apoptosis. Telomerase (reverse transcriptase with RNA template) maintains telomere length in germline, stem cells, and most cancer cells. Telomerase as a cancer target. NEET PG: ageing, Werner syndrome (premature ageing), cancer biology.",
            "टेलोमेयर — गुणसूत्र की सुरक्षात्मक टोपी",
            "Cancer cells reactivate telomerase to maintain their telomeres, enabling unlimited replication.",
            "chromosomal end cap",
            "Greek: telos (end) + meros (part)",
            9, 70),

        BioTerm("signal transduction", "/ˈsɪɡnəl trænsˈdʌkʃən/", "noun",
            "Cascade of molecular events by which an extracellular signal is converted to an intracellular response.",
            "Components: receptor (surface or nuclear) → second messenger generation → kinase cascade → transcription factor activation → gene expression. cAMP pathway: ligand → Gs protein → adenylyl cyclase → cAMP → PKA activation (glucagon, adrenaline, TSH, PTH). IP3/DAG pathway: Gq → PLC → IP3 (Ca²⁺ release) + DAG → PKC. RTK pathway: insulin, EGF, growth factors → autophosphorylation → RAS → MAPK cascade. NEET PG: G-protein mutations in cancer (RAS oncogene).",
            "सिग्नल ट्रांसडक्शन — संकेत संचरण मार्ग",
            "Insulin binding to its receptor tyrosine kinase initiates a signal transduction cascade promoting glucose uptake.",
            "cell signalling cascade",
            "Latin: signum (sign) + transducere (to lead across)",
            9, 72),

        BioTerm("polyploidy", "/ˈpɒlɪˌplɔɪdi/", "noun",
            "Presence of more than two complete sets of chromosomes in an organism's cells.",
            "Autopolyploidy: extra sets from same species (colchicine inhibits spindle → chromosome doubling). Allopolyploidy: chromosome sets from two different species (hybridisation + doubling) — most common in plants. Example: Bread wheat (Triticum aestivum) is hexaploid (6n = 42, AABBDD genome from three ancestral species). Tetraploid: banana, potato, groundnut. Polyploidy plays a major role in plant speciation and crop improvement. Triploidy → sterility (seedless watermelon).",
            "बहुगुणसूत्रता — अतिरिक्त गुणसूत्र समुच्चय",
            "Allopolyploidy, as seen in bread wheat, combines genomes from different ancestral species.",
            "polyploid genome, chromosome doubling",
            "Greek: polys (many) + aploos (single/simple) + eidos (form)",
            8, 70),

        BioTerm("nucleosome", "/ˈnjuːklɪəˌsoʊm/", "noun",
            "Basic repeating unit of chromatin: DNA wound around an octamer of histone proteins.",
            "Core particle: 147 bp DNA wrapped ~1.65 times around H2A, H2B, H3, H4 (two copies each). Linker DNA connects nucleosomes; H1 histone stabilises. Gives 'beads-on-a-string' appearance under EM. 30 nm fibre (solenoid model): further compaction. Histone modifications: acetylation → open chromatin (active transcription); methylation → compaction (gene silencing). NEET PG: epigenetics — histone code hypothesis, DNA methylation at CpG islands.",
            "न्यूक्लियोसोम — क्रोमेटिन की मूल इकाई",
            "Histone acetylation relaxes nucleosome packing, making DNA accessible for transcription.",
            "chromatin subunit, histone octamer",
            "Latin: nucleus (kernel) + Greek soma (body)",
            8, 74),

        BioTerm("endosymbiosis", "/ˌɛndoʊˌsɪmbaɪˈoʊsɪs/", "noun",
            "Theory that mitochondria and chloroplasts evolved from free-living prokaryotes engulfed by proto-eukaryotes.",
            "Proposed by Lynn Margulis (1967). Evidence: (1) Double membranes (inner = original prokaryote, outer = phagosomal); (2) Own circular DNA; (3) 70S ribosomes (like prokaryotes); (4) Binary fission (not mitosis); (5) Inhibited by prokaryotic antibiotics. Mitochondria: α-proteobacterium ancestor. Chloroplasts: cyanobacterium ancestor. NEET: evidence is frequently tested; connect to evolution and origin of eukaryotes.",
            "अन्तःसहजीवन — माइटोकॉन्ड्रिया की उत्पत्ति का सिद्धांत",
            "The 70S ribosomes in mitochondria support the endosymbiotic origin from an ancestral prokaryote.",
            "endosymbiont theory, serial endosymbiosis",
            "Greek: endon (within) + symbiosis (living together)",
            8, 72),

        BioTerm("meristematic tissue", "/ˌmɛrɪˌstɛmætɪk ˈtɪʃuː/", "noun",
            "Undifferentiated, actively dividing plant cells responsible for growth.",
            "Types by position: Apical meristems (root tip and shoot apex — primary growth); Lateral meristems (vascular cambium and cork cambium — secondary growth/girth); Intercalary meristems (at leaf bases/grass nodes — elongation after grazing). Cells: thin-walled, large nucleus, dense cytoplasm, no vacuoles. Totipotency: each meristematic cell can regenerate entire plant (basis of tissue culture). NEET: distinguish primary vs secondary growth, role in tropisms.",
            "विभज्योतकी ऊतक — पादप वृद्धि ऊतक",
            "Apical meristems at root and shoot tips drive primary growth by continuous cell division.",
            "meristem, apical meristem",
            "Greek: meristos (divided) + Latin matica (relating to)",
            6, 78),

        BioTerm("abscisic acid", "/æbˈsɪsɪk ˈæsɪd/", "noun",
            "Plant stress hormone (ABA) that promotes stomatal closure, seed dormancy, and stress responses.",
            "Called the 'stress hormone' or 'dormin'. Synthesised in leaves, roots, stem under drought, cold, salinity. Causes stomatal closure (K⁺ efflux from guard cells). Inhibits germination and promotes seed dormancy. Counteracts gibberellin in germination. Promotes leaf senescence and abscission (historically). Involved in acclimatisation to drought — increases root hydraulic conductivity, promotes ABA-responsive gene expression. NEET: stress physiology, antagonist of GA.",
            "एब्सिसिक एसिड — तनाव हॉर्मोन",
            "ABA triggers stomatal closure under drought stress by promoting K⁺ efflux from guard cells.",
            "ABA, abscission hormone, stress hormone",
            "Latin: abscissio (cutting off) — early (incorrect) association with abscission",
            7, 70),

        BioTerm("respiratory quotient", "/rɪˈspɪrətəri ˈkwoʊʃənt/", "noun",
            "Ratio of CO₂ released to O₂ consumed during cellular respiration; indicates substrate being oxidised.",
            "RQ = CO₂ produced / O₂ consumed. Carbohydrates: RQ = 1.0 (C₆H₁₂O₆ + 6O₂ → 6CO₂ + 6H₂O). Fats: RQ ≈ 0.7 (high H:O ratio, more O₂ needed). Proteins: RQ ≈ 0.8-0.9. RQ > 1: organic acids (e.g., malic acid in CAM plants) or anaerobic fermentation. Succulents at night: RQ > 1. Germinating fatty seeds (castor): RQ < 1. Anaerobic respiration: RQ = ∞ (CO₂ produced, no O₂ consumed).",
            "श्वसन भागफल — RQ — श्वसन अनुपात",
            "During carbohydrate oxidation, the respiratory quotient equals 1.0 as equal volumes of CO₂ and O₂ are exchanged.",
            "RQ, respiratory ratio",
            "Latin: respirare (to breathe) + quotiens (how many times)",
            7, 75)
    )

    fun seed(db: SupportSQLiteDatabase) {
        try {
            db.beginTransaction()
            val now = System.currentTimeMillis()
            terms.forEach { t ->
                seedSafely(db, t, now)
            }
            db.setTransactionSuccessful()
            Log.i("BiologySeeder", "Biology seed completed: ${terms.size} terms")
        } catch (e: Exception) {
            Log.e("BiologySeeder", "Biology seed failed", e)
        } finally {
            db.endTransaction()
        }
    }

    private fun toJsonArray(csv: String): String {
        if (csv.isBlank()) return "[]"
        val items = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return "[" + items.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" } + "]"
    }

    private fun seedSafely(db: SupportSQLiteDatabase, t: BioTerm, now: Long) {
        try {
            val synonymsJson = toJsonArray(t.synonyms)
            db.execSQL("""
                INSERT OR IGNORE INTO dictionary_cache
                (word, mode, pronunciation, part_of_speech, short_meaning, detailed_meaning,
                 hindi_meaning, hindi_pronunciation, example_sentence, synonyms, antonyms,
                 etymology, difficulty_level, frequency_rating, source, created_at, last_accessed,
                 access_count, is_favorite, user_note, bio_ext_data)
                VALUES (?, 'biology', ?, ?, ?, ?, ?, '', ?, ?, '[]', ?, ?, ?, 'seed', ?, 0, 0, 0, '', '{}')
            """, arrayOf(
                t.word, t.pronunciation, t.partOfSpeech,
                t.shortMeaning, t.detailedMeaning,
                t.hindiMeaning, t.exampleSentence,
                synonymsJson, t.etymology,
                t.difficultyLevel.toString(), t.frequencyRating.toString(),
                now.toString()
            ))
        } catch (e: Exception) {
            Log.w("BiologySeeder", "Skip '${t.word}': ${e.message}")
        }
    }
}
