package com.nexa.ai.`data`.local

import androidx.collection.ArrayMap
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndex
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performInTransactionSuspending
import androidx.room.util.performSuspending
import androidx.room.util.recursiveFetchArrayMap
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlin.text.StringBuilder

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class SessionDao_Impl(
  __db: RoomDatabase,
) : SessionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSessionEntity: EntityInsertAdapter<SessionEntity>

  private val __insertAdapterOfMessageEntity: EntityInsertAdapter<MessageEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfSessionEntity = object : EntityInsertAdapter<SessionEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `sessions` (`id`,`title`,`createdAt`,`updatedAt`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SessionEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindLong(3, entity.createdAt)
        statement.bindLong(4, entity.updatedAt)
      }
    }
    this.__insertAdapterOfMessageEntity = object : EntityInsertAdapter<MessageEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `messages` (`id`,`sessionId`,`messageId`,`role`,`content`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.sessionId)
        statement.bindText(3, entity.messageId)
        statement.bindText(4, entity.role)
        statement.bindText(5, entity.content)
      }
    }
  }

  public override suspend fun insertSession(session: SessionEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfSessionEntity.insert(_connection, session)
  }

  public override suspend fun insertMessages(messages: List<MessageEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfMessageEntity.insert(_connection, messages)
  }

  public override suspend fun saveAll(sessions: List<SessionEntity>, messages: List<MessageEntity>): Unit = performInTransactionSuspending(__db) {
    super@SessionDao_Impl.saveAll(sessions, messages)
  }

  public override suspend fun getAllSessions(): List<SessionWithMessages> {
    val _sql: String = "SELECT * FROM sessions ORDER BY updatedAt DESC"
    return performSuspending(__db, true, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _collectionMessages: ArrayMap<String, MutableList<MessageEntity>> = ArrayMap<String, MutableList<MessageEntity>>()
        while (_stmt.step()) {
          val _tmpKey: String
          _tmpKey = _stmt.getText(_columnIndexOfId)
          if (!_collectionMessages.containsKey(_tmpKey)) {
            _collectionMessages.put(_tmpKey, mutableListOf())
          }
        }
        _stmt.reset()
        __fetchRelationshipmessagesAscomNexaAiDataLocalMessageEntity(_connection, _collectionMessages)
        val _result: MutableList<SessionWithMessages> = mutableListOf()
        while (_stmt.step()) {
          val _item: SessionWithMessages
          val _tmpSession: SessionEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _tmpSession = SessionEntity(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpUpdatedAt)
          val _tmpMessagesCollection: MutableList<MessageEntity>
          val _tmpKey_1: String
          _tmpKey_1 = _stmt.getText(_columnIndexOfId)
          _tmpMessagesCollection = _collectionMessages.getValue(_tmpKey_1)
          _item = SessionWithMessages(_tmpSession,_tmpMessagesCollection)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteSession(sessionId: String) {
    val _sql: String = "DELETE FROM sessions WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllMessages() {
    val _sql: String = "DELETE FROM messages"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM sessions"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteMessagesForSession(sessionId: String) {
    val _sql: String = "DELETE FROM messages WHERE sessionId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  private fun __fetchRelationshipmessagesAscomNexaAiDataLocalMessageEntity(_connection: SQLiteConnection, _map: ArrayMap<String, MutableList<MessageEntity>>) {
    val __mapKeySet: Set<String> = _map.keys
    if (__mapKeySet.isEmpty()) {
      return
    }
    if (_map.size > 999) {
      recursiveFetchArrayMap(_map, true) { _tmpMap ->
        __fetchRelationshipmessagesAscomNexaAiDataLocalMessageEntity(_connection, _tmpMap)
      }
      return
    }
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT `id`,`sessionId`,`messageId`,`role`,`content` FROM `messages` WHERE `sessionId` IN (")
    val _inputSize: Int = __mapKeySet.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    val _stmt: SQLiteStatement = _connection.prepare(_sql)
    var _argIndex: Int = 1
    for (_item: String in __mapKeySet) {
      _stmt.bindText(_argIndex, _item)
      _argIndex++
    }
    try {
      val _itemKeyIndex: Int = getColumnIndex(_stmt, "sessionId")
      if (_itemKeyIndex == -1) {
        return
      }
      val _columnIndexOfId: Int = 0
      val _columnIndexOfSessionId: Int = 1
      val _columnIndexOfMessageId: Int = 2
      val _columnIndexOfRole: Int = 3
      val _columnIndexOfContent: Int = 4
      while (_stmt.step()) {
        val _tmpKey: String
        _tmpKey = _stmt.getText(_itemKeyIndex)
        val _tmpRelation: MutableList<MessageEntity>? = _map.get(_tmpKey)
        if (_tmpRelation != null) {
          val _item_1: MessageEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpSessionId: String
          _tmpSessionId = _stmt.getText(_columnIndexOfSessionId)
          val _tmpMessageId: String
          _tmpMessageId = _stmt.getText(_columnIndexOfMessageId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          _item_1 = MessageEntity(_tmpId,_tmpSessionId,_tmpMessageId,_tmpRole,_tmpContent)
          _tmpRelation.add(_item_1)
        }
      }
    } finally {
      _stmt.close()
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
