import {Request, Response} from 'express';
import {getPool} from '../../db/progress';

export async function listStories(req: Request, res: Response) {
  const pool = getPool();
  const userId = (req as any).userId;

  // Get all stories with dynamic node count
  const storiesResult = await pool.query(`
    SELECT s.*,
      (SELECT COUNT(*) FROM story_nodes n WHERE n.story_id = s.id) AS node_count
    FROM adventure_stories s
    ORDER BY s.sort_order
  `);

  // Get user progress for each story
  const progressResult = userId
    ? await pool.query(
        `SELECT story_id, current_node_id, words_learned, completed FROM story_progress WHERE chat_id = $1`,
        [userId],
      )
    : {rows: []};

  const progressMap = new Map(progressResult.rows.map((r: any) => [r.story_id, r]));

  const stories = storiesResult.rows.map((s: any) => ({
    id: s.id,
    title: s.title,
    subtitle_tr: s.subtitle_tr,
    subtitle_en: s.subtitle_en,
    genre_tr: s.genre_tr,
    genre_en: s.genre_en,
    emoji: s.emoji,
    color: s.color,
    cefr_level: s.cefr_level,
    nodeCount: parseInt(s.node_count, 10),
    progress: progressMap.get(s.id) || null,
  }));

  res.json({stories});
}

export async function getStory(req: Request, res: Response) {
  const pool = getPool();
  const {storyId} = req.params;

  // Get story meta
  const storyResult = await pool.query(`SELECT * FROM adventure_stories WHERE id = $1`, [storyId]);
  if (storyResult.rows.length === 0) {
    return res.status(404).json({error: 'Story not found'});
  }
  const story = storyResult.rows[0];

  // Get all nodes
  const nodesResult = await pool.query(
    `SELECT * FROM story_nodes WHERE story_id = $1 ORDER BY sort_order`,
    [storyId],
  );

  // Get all vocabulary
  const vocabResult = await pool.query(
    `SELECT * FROM story_node_vocabulary WHERE story_id = $1 ORDER BY sort_order`,
    [storyId],
  );

  // Get all choices
  const choicesResult = await pool.query(
    `SELECT * FROM story_node_choices WHERE story_id = $1 ORDER BY sort_order`,
    [storyId],
  );

  // Get all minigame triggers
  const minigameResult = await pool.query(
    `SELECT * FROM story_node_minigame WHERE story_id = $1`,
    [storyId],
  );

  // Build vocab map: nodeId -> vocab[]
  const vocabMap = new Map<string, any[]>();
  for (const v of vocabResult.rows) {
    const list = vocabMap.get(v.node_id) || [];
    list.push({word: v.word, translation: v.translation, context_hint: v.context_hint, word_id: v.word_id});
    vocabMap.set(v.node_id, list);
  }

  // Build choices map: nodeId -> choices[]
  const choicesMap = new Map<string, any[]>();
  for (const c of choicesResult.rows) {
    const list = choicesMap.get(c.node_id) || [];
    list.push({
      choice_id: c.id,
      text_ee: c.text_ee,
      text_tr: c.text_tr,
      is_correct_grammar: c.is_correct_grammar,
      feedback_ee: c.feedback_ee,
      feedback_tr: c.feedback_tr,
      nextNode_id: c.next_node_id,
    });
    choicesMap.set(c.node_id, list);
  }

  // Build minigame map: nodeId -> trigger
  const minigameMap = new Map<string, any>();
  for (const m of minigameResult.rows) {
    minigameMap.set(m.node_id, {
      isActive: true,
      game_type: m.game_type,
      target_words: m.target_words,
      completion_node_id: m.completion_node_id,
    });
  }

  const NO_GAME = {isActive: false, game_type: null, target_words: [], completion_node_id: ''};

  // Assemble nodes
  const nodes = nodesResult.rows.map((n: any) => ({
    node_id: n.id,
    stage: n.stage,
    language_level: n.language_level,
    speaker: n.speaker,
    scene: n.scene,
    text_ee: n.text_ee,
    text_tr: n.text_tr,
    vocabulary_focus: vocabMap.get(n.id) || [],
    choices: choicesMap.get(n.id) || [],
    minigame_trigger: minigameMap.get(n.id) || NO_GAME,
  }));

  // Get first node ID
  const firstNodeId = nodes.length > 0 ? nodes[0].node_id : '';

  res.json({
    id: story.id,
    title: story.title,
    subtitle_tr: story.subtitle_tr,
    subtitle_en: story.subtitle_en,
    genre_tr: story.genre_tr,
    genre_en: story.genre_en,
    emoji: story.emoji,
    color: story.color,
    cefr_level: story.cefr_level,
    firstNodeId,
    nodeCount: nodes.length,
    nodes,
  });
}

export async function saveProgress(req: Request, res: Response) {
  const pool = getPool();
  const userId = (req as any).userId;
  const {storyId} = req.params;
  const {currentNodeId, wordsLearned, completed} = req.body;

  await pool.query(
    `INSERT INTO story_progress (chat_id, story_id, current_node_id, words_learned, completed, completed_at, updated_at)
     VALUES ($1, $2, $3, $4, $5, $6, NOW())
     ON CONFLICT (chat_id, story_id) DO UPDATE SET
       current_node_id = $3,
       words_learned = $4,
       completed = $5,
       completed_at = $6,
       updated_at = NOW()`,
    [userId, storyId, currentNodeId, wordsLearned || 0, completed || false, completed ? new Date() : null],
  );

  res.json({ok: true});
}

export async function getProgress(req: Request, res: Response) {
  const pool = getPool();
  const userId = (req as any).userId;
  const {storyId} = req.params;

  const result = await pool.query(
    `SELECT * FROM story_progress WHERE chat_id = $1 AND story_id = $2`,
    [userId, storyId],
  );

  if (result.rows.length === 0) {
    return res.json({progress: null});
  }

  const p = result.rows[0];
  res.json({
    progress: {
      currentNodeId: p.current_node_id,
      wordsLearned: p.words_learned,
      completed: p.completed,
      startedAt: p.started_at,
      updatedAt: p.updated_at,
    },
  });
}
