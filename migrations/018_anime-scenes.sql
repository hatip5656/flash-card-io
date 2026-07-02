-- Update anime story nodes to use proper scene types

-- Küla Kaitsja (Naruto)
UPDATE story_nodes SET scene = 'village' WHERE story_id = 'village_hero' AND id IN ('n1_alone', 'n1_end');
UPDATE story_nodes SET scene = 'training_ground' WHERE story_id = 'village_hero' AND id IN ('n1_master', 'n1_training', 'n1_gate1');

-- Müüride Taga (Attack on Titan)
UPDATE story_nodes SET scene = 'walled_city' WHERE story_id = 'behind_walls' AND id IN ('w1_city', 'w1_eren', 'w1_friend');
UPDATE story_nodes SET scene = 'wall_breach' WHERE story_id = 'behind_walls' AND id IN ('w1_quake', 'w1_breach', 'w1_gate1');

-- Mere Kuningas (One Piece)
UPDATE story_nodes SET scene = 'open_sea' WHERE story_id = 'sea_king' AND id IN ('s1_dream', 's1_boat', 's1_sail', 's1_gate1', 's1_end');
UPDATE story_nodes SET scene = 'tropical_island' WHERE story_id = 'sea_king' AND id IN ('s1_island', 's1_explore_island', 's1_gate2');

-- Vaimude Maailm (Spirited Away)
UPDATE story_nodes SET scene = 'spirit_town' WHERE story_id = 'spirit_world' AND id IN ('v1_other_side');
UPDATE story_nodes SET scene = 'bathhouse' WHERE story_id = 'spirit_world' AND id IN ('v1_bathhouse', 'v1_witch', 'v1_gate1', 'v1_work', 'v1_gate2');

-- Saatuse Raamat (Death Note)
UPDATE story_nodes SET scene = 'dark_room' WHERE story_id = 'fate_book' AND id IN ('d1_test', 'd1_dilemma', 'd1_choice');
