package io.github.atmstudent.kakukaku;

import android.util.Xml;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;
import com.j256.ormlite.table.TableUtils;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;

import io.github.atmstudent.kakukaku.Database.IDatabaseHelper;
import io.github.atmstudent.kakukaku.Database.JmDictDatabase.Models.EntryOptimized;
import io.github.atmstudent.kakukaku.XmlParsers.JmDict.JmParser;
import io.github.atmstudent.kakukaku.XmlParsers.KanjiDict2.Kd2Parser;

public class GenerateDictionary
{
    class DatabaseHelperImpl implements IDatabaseHelper {

        ConnectionSource mConnectionSource;

        DatabaseHelperImpl(ConnectionSource connectionSource){
            mConnectionSource = connectionSource;
        }

        @Override
        public <T> Dao<T, Integer> getDbDao(Class clazz) throws SQLException
        {
            return DaoManager.createDao(mConnectionSource, clazz);
        }
    }

    /**
     * Reads an XML file without its DOCTYPE block. The embedded DTD (entity declarations and long
     * comments) is more than the parser copes with; the dictionary parsers read entities such as
     * &n; themselves, so the declarations are not needed.
     */
    private static String withoutDoctype(String path) throws Exception
    {
        String xml = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);

        int start = xml.indexOf("<!DOCTYPE");
        if (start < 0) return xml;

        int bracket = xml.indexOf('[', start);
        int firstClose = xml.indexOf('>', start);
        int end = (bracket >= 0 && bracket < firstClose) ? xml.indexOf("]>", bracket) + 2 : firstClose + 1;

        return xml.substring(0, start) + xml.substring(end);
    }

    /**
     * This isn't actually a test: it generates the SQLite dictionary bundled with the app from the
     * official JMdict and KANJIDIC2 XML files (decompressed). It is skipped unless the environment
     * variable KAKUKAKU_DB_OUT is set:
     *
     *   KAKUKAKU_JMDICT_XML    path of JMdict_e (decompressed)
     *   KAKUKAKU_KANJIDIC_XML  path of kanjidic2.xml (decompressed)
     *   KAKUKAKU_DB_OUT        path of the database to create
     *
     *   ./gradlew testDebugUnitTest --tests io.github.atmstudent.kakukaku.GenerateDictionary
     *
     * See README.md ("Rebuilding the bundled dictionary").
     */
    @Test
    public void generateDic() throws Exception {

        String dbPath = System.getenv("KAKUKAKU_DB_OUT");
        Assume.assumeTrue("KAKUKAKU_DB_OUT is not set", dbPath != null);

        String jmdictPath = System.getenv("KAKUKAKU_JMDICT_XML");
        String kanjidicPath = System.getenv("KAKUKAKU_KANJIDIC_XML");
        Assert.assertNotNull("KAKUKAKU_JMDICT_XML is not set", jmdictPath);
        Assert.assertNotNull("KAKUKAKU_KANJIDIC_XML is not set", kanjidicPath);

        // Without these every inserted row is its own synced transaction, which takes hours
        String databaseUrl = String.format("jdbc:sqlite:%s?journal_mode=MEMORY&synchronous=OFF", dbPath);

        Files.deleteIfExists(Paths.get(dbPath));

        ConnectionSource connectionSource = null;
        try
        {
            connectionSource = new JdbcConnectionSource(databaseUrl);

            TableUtils.createTable(connectionSource, EntryOptimized.class);

            DatabaseHelperImpl dbHelper = new DatabaseHelperImpl(connectionSource);

            XmlPullParser jmdictParser = new KXmlParser();
            jmdictParser.setInput(new StringReader(withoutDoctype(jmdictPath)));
            new JmParser(dbHelper).parseDict(jmdictParser);

            XmlPullParser kanjidicParser = new KXmlParser();
            kanjidicParser.setInput(new StringReader(withoutDoctype(kanjidicPath)));
            new Kd2Parser(dbHelper).parseDict(kanjidicParser);

            // What Android's SQLiteOpenHelper expects to find in a prebuilt database (version 1, see JmDatabaseHelper)
            DatabaseConnection connection = connectionSource.getReadWriteConnection(null);
            connection.executeStatement("CREATE TABLE android_metadata (locale TEXT)", DatabaseConnection.DEFAULT_RESULT_FLAGS);
            connection.executeStatement("INSERT INTO android_metadata VALUES ('en_US')", DatabaseConnection.DEFAULT_RESULT_FLAGS);
            connection.executeStatement("PRAGMA user_version = 1", DatabaseConnection.DEFAULT_RESULT_FLAGS);

        } finally {
            if (connectionSource != null) {
                connectionSource.close();
            }
        }
    }
}
