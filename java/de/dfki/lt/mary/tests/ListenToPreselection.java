/**
 * Copyright 2006 DFKI GmbH.
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
package de.dfki.lt.mary.tests;

import de.dfki.lt.mary.unitselection.*;
import de.dfki.lt.mary.unitselection.Unit;
import de.dfki.lt.mary.unitselection.voiceimport_reorganized.*;
import de.dfki.lt.mary.unitselection.featureprocessors.FeatureDefinition;
import de.dfki.lt.mary.unitselection.featureprocessors.FeatureVector;
//import de.dfki.lt.mary.unitselection.voiceimport_reorganized.WavReader;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.util.Vector;

public class ListenToPreselection {
    
    private static int byteswap( int val ) {
       return( ( (val & 0xff000000) >>> 24)
                + ( (val & 0x00ff0000) >>> 8)
                + ( (val & 0x0000ff00) << 8)
                + ( (val & 0x000000ff) << 24) );
    }
    
    private static short byteswap( short val ) {
        return( (short)(
                ( ((int)(val) & 0xff00) >>> 8 )
                +
                ( ((int)(val) & 0x00ff) << 8)
                )
              );
    }
    
    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {

        DatabaseLayout dbl = new DatabaseLayout();
        UnitFileReader ufr = new UnitFileReader( dbl.unitFileName() );
        TimelineReader tlr = new TimelineReader( dbl.waveTimelineFileName() );
        //TimelineReader tlr = new TimelineReader( dbl.lpcTimelineFileName() );
        FeatureFileIndexer ffi = new FeatureFileIndexer( dbl.targetFeaturesFileName() );
        FeatureDefinition feaDef = ffi.getFeatureDefinition();
        
        System.out.println( "Indexing the phonemes..." );
        String[] feaSeq = { "mary_phoneme" }; // Sort by phoneme name
        ffi.deepSort( feaSeq );
        
        /* Loop across possible phonemes */
        long tic = System.currentTimeMillis();
        int nbPhonVal = feaDef.getNumberOfValues( feaDef.getFeatureIndex( "mary_phoneme" ) );
        for ( int phon = 1; phon < nbPhonVal; phon++ ) {
        // for ( int phon = 14; phon < nbPhonVal; phon++ ) {
            String phonID = feaDef.getFeatureValueAsString( 0, phon );
            /* Loop across all instances */
            byte[] phonFeature = new byte[1];
            phonFeature[0] = (byte)( phon );
            FeatureVector target = new FeatureVector( phonFeature, null, null, 0 );
            FeatureFileIndexingResult instances = ffi.retrieve( target );
            int[] ui = instances.getUnitIndexes();
            System.out.println( "Concatenating the phoneme [" + phonID + "] which has [" + ui.length + "] instances..." );
            ByteArrayOutputStream bbis = new ByteArrayOutputStream();
            /* Concatenate the instances */
            for ( int i = 0; i < ui.length; i++ ) {
                /* Concatenate the datagrams from the instances */
                Datagram[] dat = tlr.getDatagrams( ufr.getUnit(ui[i]), ufr.getSampleRate() );
                for ( int k = 0; k < dat.length; k++ ) {
                    bbis.write( (byte[])dat[k].getData() );
                }
            }
            /* Get the bytes as an array */
            byte[] buf = bbis.toByteArray();
            /* Output the header of the wav file */
            System.out.println( "Outputting file [" + ( dbl.rootDirName() + "/tests/" + phonID + ".wav" ) + "]..." );
            DataOutputStream dos = new DataOutputStream( new BufferedOutputStream(
                    new FileOutputStream( dbl.rootDirName() + "/tests/" + phonID + ".wav" ) ) );
            dos.writeBytes( "RIFF" ); // "RIFF" in ascii
            dos.writeInt( byteswap(36 + buf.length) ); // Chunk size
            dos.writeBytes( "WAVEfmt " );
            dos.writeInt( byteswap(16) ); // chunk size, 16 for PCM
            dos.writeShort( byteswap( (short)1 ) ); // PCM format
            dos.writeShort( byteswap( (short)1 ) ); // Mono, one channel
            dos.writeInt( byteswap( 16000 ) ); // Samplerate
            dos.writeInt( byteswap( 16000 * 2 ) ); // Byte-rate
            dos.writeShort( byteswap(  (short)2 ) ); // Nbr of bytes per samples x nbr of channels
            dos.writeShort( byteswap( (short)16 ) ); // nbr of bits per sample
            dos.writeBytes( "data" );
            dos.writeInt( byteswap(buf.length) );
            System.out.println("NB BYTES IN DATA=" + buf.length + " meaning " + (buf.length/2) + " samples." );
            /* Byte-swap the buffer: */
            byte b = 0;
            for ( int j = 0; j < buf.length; j += 2 ) {
                b = buf[j];
                buf[j] = buf[j+1];
                buf[j+1] = b;
            }
            dos.write( buf );
            dos.close();
            /* Sanity check */
            /* WavReader wr = new WavReader( dbl.rootDirName() + "/tests/" + phonID + ".wav" );
            System.out.println( "File [" + ( dbl.rootDirName() + "/tests/" + phonID + ".wav" )
                    + "] has [" + wr.getNumSamples() + "] samples." ); */
        }
        long toc = System.currentTimeMillis();
        System.out.println( "Copying the phonemes took [" + (toc-tic) + "] milliseconds." );
    }

}
