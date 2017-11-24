/**
 * Copyright 2002-2008 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package marytts.language.ru;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.parsers.ParserConfigurationException;

import marytts.data.Utterance;

import marytts.exceptions.MaryConfigurationException;
import marytts.fst.FSTLookup;
import marytts.modules.MaryModule;
import marytts.modules.nlp.phonemiser.AllophoneSet;
import marytts.config.MaryConfiguration;
import marytts.util.MaryRuntimeUtils;
import marytts.MaryException;
import marytts.data.Utterance;
import marytts.data.Sequence;
import marytts.data.Relation;
import marytts.data.SupportedSequenceType;
import marytts.data.utils.IntegerPair;
import marytts.data.item.linguistic.Word;
import marytts.data.item.phonology.Phoneme;
import marytts.data.item.phonology.Syllable;
import marytts.data.item.phonology.Accent;

import com.google.common.base.Splitter;

import org.apache.logging.log4j.core.Appender;
/**
 * Russian phonemiser module
 *
 * @author Nickolay V. Shmyrev, Marc Schr&ouml;der, Sathish
 */

public class Phonemiser extends MaryModule {

    protected final String SYL_SEP = "-";
    protected final String FIRST_STRESS = "'";
    protected final String SECOND_STRESS = ",";

    // TODO: This reads the userdict only. Replace with a mechanism that can
    // deal with FST lexicon and LTS rules

    protected Map<String, List<String>> userdict;
    protected FSTLookup lexicon;

    protected AllophoneSet allophoneSet;

    public Phonemiser(String propertyPrefix)
    throws IOException, ParserConfigurationException, MaryConfigurationException {
        this("Phonemiser", propertyPrefix + "allophoneset", propertyPrefix + "userdict");
    }

    /**
     * Constructor providing the individual filenames of files that are
     * required.
     *
     * @param componentName
     *            componentName
     * @param allophonesProperty
     *            allophonesProperty
     * @param userdictProperty
     *            userdictProperty
     * @throws IOException
     *             IOException
     * @throws ParserConfigurationException
     *             ParserConfigurationException
     * @throws MaryConfigurationException
     *             MaryConfigurationException
     */
    public Phonemiser(String componentName, String allophonesProperty, String userdictProperty)
    throws IOException, ParserConfigurationException, MaryConfigurationException {
        super(componentName, MaryRuntimeUtils.needAllophoneSet(allophonesProperty).getLocale());
        allophoneSet = MaryRuntimeUtils.needAllophoneSet(allophonesProperty);

        // // userdict is optional
        // // Actually here, the user dict is the only source of information we
        // // have, so it is not optional:
        // String userdictFilename = MaryProperties.needFilename(userdictProperty);
        // if (userdictFilename != null) {
        //     userdict = readLexicon(userdictFilename);
        // }
    }

    /**
     *  Check if the input contains all the information needed to be
     *  processed by the module.
     *
     *  @param utt the input utterance
     *  @throws MaryException which indicates what is missing if something is missing
     */
    public void checkInput(Utterance utt) throws MaryException {
        if (!utt.hasSequence(SupportedSequenceType.SENTENCE)) {
            throw new MaryException("Sentence sequence is missing", null);
        }
        if (!utt.hasSequence(SupportedSequenceType.WORD)) {
            throw new MaryException("Word sequence is missing", null);
        }
    }

    public Utterance process(Utterance utt, MaryConfiguration configuration, Appender app) throws Exception {

        Sequence<Word> words = (Sequence<Word>) utt.getSequence(SupportedSequenceType.WORD);
        Sequence<Syllable> syllables = new Sequence<Syllable>();
        ArrayList<IntegerPair> alignment_word_syllable = new ArrayList<IntegerPair>();

        Sequence<Phoneme> phones = new Sequence<Phoneme>();
        ArrayList<IntegerPair> alignment_syllable_phone = new ArrayList<IntegerPair>();

        Relation rel_words_sent = utt.getRelation(SupportedSequenceType.SENTENCE,
                                  SupportedSequenceType.WORD)
                                  .getReverse();
        HashSet<IntegerPair> alignment_word_phrase = new HashSet<IntegerPair>();

        for (int i_word = 0; i_word < words.size(); i_word++) {
            Word w = words.get(i_word);

            String text;

            if (w.soundsLike() != null) {
                text = w.soundsLike();
            } else {
                text = w.getText();
            }

            // Get POS
            String pos = w.getPOS();

            // Ok adapt phonemes now
            ArrayList<String> phonetisation_string = new ArrayList<String>();
            if ((text != null) && (!text.equals(""))) {

                // If text consists of several parts (e.g., because that was
                // inserted into the sounds_like attribute), each part
                // is transcribed separately.
                StringBuilder ph = new StringBuilder();
                String g2p_method = null;
                StringTokenizer st = new StringTokenizer(text, " -");
                while (st.hasMoreTokens()) {
                    String graph = st.nextToken();
                    StringBuilder helper = new StringBuilder();
                    if (pos.equals("$PUNCT")) {
                        continue;
                    }

                    String phon = phonemise(graph, pos, helper);

                    // FIXME: what does it mean : null result should not be
                    // processed
                    if (phon == null) {
                        continue;
                    }

                    if (ph.length() == 0) {
                        g2p_method = helper.toString();
                    }

                    phonetisation_string.add(phon);
                }

                if (phonetisation_string.size() > 0) {

                    createSubStructure(w, phonetisation_string, allophoneSet, syllables, phones,
                                       alignment_syllable_phone, i_word, alignment_word_syllable);

                    // Adapt G2P method
                    w.setG2PMethod(g2p_method);
                }
            }
        }

        // Relation word/syllable
        utt.addSequence(SupportedSequenceType.SYLLABLE, syllables);
        Relation rel_word_syllable = new Relation(words, syllables, alignment_word_syllable);
        utt.setRelation(SupportedSequenceType.WORD, SupportedSequenceType.SYLLABLE, rel_word_syllable);

        utt.addSequence(SupportedSequenceType.PHONE, phones);
        Relation rel_syllable_phone = new Relation(syllables, phones, alignment_syllable_phone);
        utt.setRelation(SupportedSequenceType.SYLLABLE, SupportedSequenceType.PHONE, rel_syllable_phone);

        return utt;
    }

    protected void createSubStructure(Word w, ArrayList<String> phonetisation_string,
                                      AllophoneSet allophoneSet,
                                      Sequence<Syllable> syllables, Sequence<Phoneme> phones,
                                      ArrayList<IntegerPair> alignment_syllable_phone,
                                      int word_index, ArrayList<IntegerPair> alignment_word_syllable) throws Exception {

        int stress = 0;
        int phone_offset = phones.size();
        Accent accent = null;
        Phoneme tone = null;
        for (String syl_string : phonetisation_string) {
            if (syl_string.trim().isEmpty()) {
                continue;
            }

            logger.info("Dealing with \"" + syl_string + "\"");
            Splitter syl_string_plitter = Splitter.on(' ').omitEmptyStrings().trimResults();

            Iterable<String> syl_tokens = syl_string_plitter.split(syl_string);
            for (String token : syl_tokens) {

                // Syllable separator
                if (token.equals(SYL_SEP)) {
                    // Create the syllable
                    syllables.add(new Syllable(tone, stress, accent)); // FIXME:
                    // ho to
                    // get
                    // the
                    // tone
                    // ?

                    // Update the syllable/Word relation
                    alignment_word_syllable.add(new IntegerPair(word_index, syllables.size() - 1));

                    // Update the phone/syllable relation
                    for (; phone_offset < phones.size(); phone_offset++) {
                        alignment_syllable_phone.add(new IntegerPair(syllables.size() - 1, phone_offset));
                    }

                    // Reinit for the next part
                    tone = null;
                    stress = 0;
                    accent = null;
                }
                // First stress
                else if (token.equals(FIRST_STRESS)) {
                    stress = 1;
                    accent = w.getAccent();
                }
                // Second stress
                else if (token.equals(SECOND_STRESS)) {
                    stress = 2;
                } else {
                    Phoneme cur_ph = new Phoneme(token);
                    phones.add(cur_ph);
                }
            }

            // Create the syllable
            syllables.add(new Syllable(tone, stress, accent)); // FIXME: ho to
            // get the tone
            // ?

            // Update the syllable/Word relation
            alignment_word_syllable.add(new IntegerPair(word_index, syllables.size() - 1));

            // Update the phone/syllable relation
            for (; phone_offset < phones.size(); phone_offset++) {
                alignment_syllable_phone.add(new IntegerPair(syllables.size() - 1, phone_offset));
            }
        }
    }

    /**
     * Phonemise the word text. This starts with a simple lexicon lookup,
     * followed by some heuristics, and finally applies letter-to-sound rules if
     * nothing else was successful.
     *
     * @param text
     *            the textual (graphemic) form of a word.
     * @param pos
     *            the part-of-speech of the word
     * @param g2pMethod
     *            This is an awkward way to return a second String parameter via
     *            a StringBuilder. If a phonemisation of the text is found, this
     *            parameter will be filled with the method of phonemisation
     *            ("lexicon", ... "rules").
     * @return a phonemisation of the text if one can be generated, or null if
     *         no phonemisation method was successful.
     * @throws IOException
     *             IOException
     */
    public String phonemise(String text, String pos, StringBuilder g2pMethod) throws IOException {
        // First, try a simple userdict lookup:

        String result = userdictLookup(text, pos);
        if (result != null) {
            g2pMethod.append("userdict");
            return result;
        }
        return null;
    }

    /**
     * look a given text up in the userdict. part-of-speech is used in case of
     * ambiguity.
     *
     * @param text
     *            text
     * @param pos
     *            pos
     * @return null if userdict == null || text == null || text.length() == 0,
     *         null if entries == null, transcr otherwise
     */
    public String userdictLookup(String text, String pos) {
        if (userdict == null || text == null || text.length() == 0) {
            return null;
        }
        List<String> entries = userdict.get(text);
        // If entry is not found directly, try the following changes:
        // - lowercase the word
        // - all lowercase but first uppercase
        if (entries == null) {
            text = text.toLowerCase(getLocale());
            entries = userdict.get(text);
        }
        if (entries == null) {
            text = text.substring(0, 1).toUpperCase(getLocale()) + text.substring(1);
            entries = userdict.get(text);
        }

        if (entries == null) {
            return null;
        }

        String transcr = null;
        for (String entry : entries) {
            String[] parts = entry.split("\\|");
            transcr = parts[0];
            if (parts.length > 1 && pos != null) {
                StringTokenizer tokenizer = new StringTokenizer(entry);
                while (tokenizer.hasMoreTokens()) {
                    String onePos = tokenizer.nextToken();
                    if (pos.equals(onePos)) {
                        return transcr;    // found
                    }
                }
            }
        }
        // no match of POS: return last entry
        return transcr;
    }

    /**
     * Read a lexicon. Lines must have the format
     *
     * graphemestring | phonestring | optional-parts-of-speech
     *
     * The pos-item is optional. Different pos's belonging to one grapheme chain
     * may be separated by whitespace
     *
     *
     * @param lexiconFilename
     *            lexiconFilename
     * @throws IOException
     *             IOException
     * @return fLexicon
     */
    protected Map<String, List<String>> readLexicon(String lexiconFilename) throws IOException {
        String line;
        Map<String, List<String>> fLexicon = new HashMap<String, List<String>>();

        BufferedReader lexiconFile = new BufferedReader(
            new InputStreamReader(new FileInputStream(lexiconFilename), "UTF-8"));
        while ((line = lexiconFile.readLine()) != null) {
            // Ignore empty lines and comments:
            if (line.trim().equals("") || line.startsWith("#")) {
                continue;
            }

            String[] lineParts = line.split("\\s*\\|\\s*");
            String graphStr = lineParts[0];
            String phonStr = lineParts[1];
            try {
                allophoneSet.splitIntoAllophones(phonStr);
            } catch (RuntimeException re) {
                logger.warn("Lexicon '" + lexiconFilename + "': invalid entry for '" + graphStr + "'", re);
            }
            String phonPosStr = phonStr;
            if (lineParts.length > 2) {
                String pos = lineParts[2];
                if (!pos.trim().equals("")) {
                    phonPosStr += "|" + pos;
                }
            }

            List<String> transcriptions = fLexicon.get(graphStr);
            if (null == transcriptions) {
                transcriptions = new ArrayList<String>();
                fLexicon.put(graphStr, transcriptions);
            }
            transcriptions.add(phonPosStr);
        }
        lexiconFile.close();
        return fLexicon;
    }
}
