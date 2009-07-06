/**
 * Copyright 2007 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */

package marytts.unitselection.concat;

import java.util.List;

import javax.sound.sampled.AudioInputStream;

import marytts.unitselection.concat.OverlapUnitConcatenator.OverlapUnitData;
import marytts.unitselection.data.Datagram;
import marytts.unitselection.data.Unit;
import marytts.unitselection.select.SelectedUnit;
import marytts.util.data.BufferedDoubleDataSource;
import marytts.util.data.DoubleDataSource;
import marytts.util.data.audio.DDSAudioInputStream;

/**
 * @author oytun.turk
 *
 */
public class HnmUnitConcatenator extends OverlapUnitConcatenator {
    
    public HnmUnitConcatenator()
    {
        super();
    }

    /**
     * Generate audio to match the target pitchmarks as closely as possible.
     * @param units
     * @return
     */
    protected AudioInputStream generateAudioStream(List<SelectedUnit> units)
    {
        int len = units.size();
        Datagram[][] datagrams = new Datagram[len][];
        Datagram[] rightContexts = new Datagram[len];
        for (int i=0; i<len; i++) {
            SelectedUnit unit = units.get(i);
            OverlapUnitData unitData = (OverlapUnitData)unit.getConcatenationData();
            assert unitData != null : "Should not have null unitdata here";
            Datagram[] frames = unitData.getFrames();
            assert frames != null : "Cannot generate audio from null frames";
            // Generate audio from frames
            datagrams[i] = frames;
            Unit nextInDB = database.getUnitFileReader().getNextUnit(unit.getUnit());
            Unit nextSelected;
            if (i+1==len) nextSelected = null;
            else nextSelected = units.get(i+1).getUnit();
            if (nextInDB != null && !nextInDB.equals(nextSelected)) {
                // Only use right context if we have a next unit in the DB is not the
                // same as the next selected unit.
                rightContexts[i] = unitData.getRightContextFrame(); // may be null
            }
        }
        
        DoubleDataSource audioSource = new DatagramHnmDoubleDataSource(datagrams, rightContexts);
        return new DDSAudioInputStream(new BufferedDoubleDataSource(audioSource), audioformat);
    }
}